package com.hoangluongtran0309.releaseflow.account;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Organization membership through single-use invitations. Administrators invite by
 * email; the invitee's Organization always comes from the invitation, never the request.
 */
@Service
public class InvitationService {

    static final String ACCEPTANCE_PATH_PREFIX = "/accept-invite#token=";
    private static final int MAX_TOKEN_LENGTH = 128;

    private final OrganizationInvitationRepository invitationRepository;
    private final AppUserRepository appUserRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom secureRandom;

    @Autowired
    InvitationService(
            OrganizationInvitationRepository invitationRepository,
            AppUserRepository appUserRepository,
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${releaseflow.invitations.ttl}") Duration ttl
    ) {
        this(invitationRepository, appUserRepository, organizationRepository, passwordEncoder, clock, ttl, new SecureRandom());
    }

    InvitationService(
            OrganizationInvitationRepository invitationRepository,
            AppUserRepository appUserRepository,
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            Duration ttl,
            SecureRandom secureRandom
    ) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalStateException("RELEASEFLOW_INVITATION_TTL must be a positive duration.");
        }
        this.invitationRepository = invitationRepository;
        this.appUserRepository = appUserRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.ttl = ttl;
        this.secureRandom = secureRandom;
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(ReleaseFlowPrincipal admin) {
        requireAdmin(admin);
        return appUserRepository.findAllByOrganizationIdOrderByCreatedAtAscIdAsc(admin.organizationId())
                .stream()
                .map(user -> new MemberView(
                        user.getId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getRole(),
                        user.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvitationView> invitations(ReleaseFlowPrincipal admin) {
        requireAdmin(admin);
        Instant now = clock.instant();
        Map<UUID, String> names = appUserRepository
                .findAllByOrganizationIdOrderByCreatedAtAscIdAsc(admin.organizationId())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName, (first, second) -> first));
        return invitationRepository.findAllByOrganizationIdOrderByCreatedAtDescIdDesc(admin.organizationId())
                .stream()
                .map(invitation -> new InvitationView(
                        invitation.getId(),
                        invitation.getEmail(),
                        invitation.effectiveStatus(now),
                        invitation.getExpiresAt(),
                        names.get(invitation.getCreatedBy()),
                        invitation.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public IssuedInvitation invite(ReleaseFlowPrincipal admin, InvitationRequest request) {
        requireAdmin(admin);
        return issue(admin, EmailAddress.canonicalize(request.getEmail()), clock.instant());
    }

    @Transactional
    public IssuedInvitation reissue(ReleaseFlowPrincipal admin, UUID invitationId) {
        requireAdmin(admin);
        Instant now = clock.instant();
        OrganizationInvitation current = find(admin, invitationId);
        if (current.isUsable(now)) {
            current.revoke(now);
        } else if (current.getStatus() == InvitationStatus.PENDING) {
            current.expire(now);
        }
        invitationRepository.flush();
        return issue(admin, current.getEmail(), now);
    }

    @Transactional
    public void revoke(ReleaseFlowPrincipal admin, UUID invitationId) {
        requireAdmin(admin);
        Instant now = clock.instant();
        OrganizationInvitation invitation = find(admin, invitationId);
        if (invitation.isUsable(now)) {
            invitation.revoke(now);
        } else if (invitation.getStatus() == InvitationStatus.PENDING) {
            invitation.expire(now);
        }
    }

    @Transactional(readOnly = true)
    public InvitationPreview inspect(String rawToken) {
        Instant now = clock.instant();
        OrganizationInvitation invitation = usableToken(rawToken)
                .flatMap(invitationRepository::findFirstByTokenHash)
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(InvalidInvitationException::new);
        String organizationName = organizationRepository.findById(invitation.getOrganizationId())
                .map(Organization::getName)
                .orElseThrow(InvalidInvitationException::new);
        return new InvitationPreview(invitation.getEmail(), organizationName, invitation.getExpiresAt());
    }

    @Transactional
    public AcceptedInvitation accept(AcceptInvitationRequest request) {
        Instant now = clock.instant();
        OrganizationInvitation invitation = usableToken(request.getToken())
                .flatMap(invitationRepository::findByTokenHash)
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(InvalidInvitationException::new);
        if (appUserRepository.existsByEmail(invitation.getEmail())) {
            throw new InvitationEmailUnavailableException();
        }

        AppUser member = new AppUser(
                UUID.randomUUID(),
                invitation.getOrganizationId(),
                invitation.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getDisplayName().strip(),
                AppUserRole.MEMBER,
                now
        );
        try {
            appUserRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException exception) {
            throw new InvitationEmailUnavailableException();
        }
        invitation.accept(member.getId(), now);
        return new AcceptedInvitation(member.getId(), member.getEmail());
    }

    private IssuedInvitation issue(ReleaseFlowPrincipal admin, String email, Instant now) {
        // Email addresses are globally unique, so an existing account anywhere blocks the invitation.
        if (appUserRepository.existsByEmail(email)) {
            throw new InvitationEmailUnavailableException();
        }
        invitationRepository.findByOrganizationIdAndEmailAndStatus(admin.organizationId(), email, InvitationStatus.PENDING)
                .ifPresent(pending -> {
                    if (pending.isUsable(now)) {
                        throw new InvitationAlreadyPendingException();
                    }
                    pending.expire(now);
                    invitationRepository.flush();
                });

        String rawToken = InvitationToken.generate(secureRandom);
        OrganizationInvitation invitation = new OrganizationInvitation(
                UUID.randomUUID(),
                admin.organizationId(),
                email,
                InvitationToken.hash(rawToken),
                admin.userId(),
                now,
                now.plus(ttl)
        );
        try {
            invitationRepository.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException exception) {
            throw new InvitationAlreadyPendingException();
        }
        return new IssuedInvitation(
                invitation.getId(),
                email,
                ACCEPTANCE_PATH_PREFIX + rawToken,
                invitation.getExpiresAt()
        );
    }

    private OrganizationInvitation find(ReleaseFlowPrincipal admin, UUID invitationId) {
        return invitationRepository.findByIdAndOrganizationId(invitationId, admin.organizationId())
                .orElseThrow(InvitationNotFoundException::new);
    }

    private static Optional<String> usableToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(InvitationToken.hash(rawToken));
    }

    // Defense in depth: the URL rules already restrict these operations to administrators.
    private static void requireAdmin(ReleaseFlowPrincipal principal) {
        if (principal.role() != AppUserRole.ADMIN) {
            throw new AccessDeniedException("Administrator role required.");
        }
    }
}
