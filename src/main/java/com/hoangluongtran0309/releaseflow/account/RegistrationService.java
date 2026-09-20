package com.hoangluongtran0309.releaseflow.account;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RegistrationService {

    private static final int MAX_SLUG_ATTEMPTS = 50;

    private final OrganizationRepository organizationRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RegistrationService(
            OrganizationRepository organizationRepository,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events,
            Clock clock
    ) {
        this.organizationRepository = organizationRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public RegistrationResult register(RegistrationRequest request) {
        String canonicalEmail = EmailAddress.canonicalize(request.getEmail());
        if (appUserRepository.existsByEmail(canonicalEmail)) {
            throw new DuplicateEmailException();
        }

        OutputLanguage outputLanguage = request.getOutputLanguage() == null || request.getOutputLanguage().isBlank()
                ? OutputLanguage.DEFAULT
                : OutputLanguage.parse(request.getOutputLanguage());
        Instant createdAt = clock.instant();
        String organizationName = request.getOrganizationName().strip();
        Organization organization = new Organization(
                UUID.randomUUID(),
                organizationName,
                availableSlug(organizationName),
                outputLanguage,
                createdAt
        );
        AppUser admin = new AppUser(
                UUID.randomUUID(),
                organization.getId(),
                canonicalEmail,
                passwordEncoder.encode(request.getPassword()),
                request.getDisplayName().strip(),
                AppUserRole.ADMIN,
                createdAt
        );

        organizationRepository.save(organization);
        try {
            appUserRepository.saveAndFlush(admin);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateEmailException();
        }
        events.publishEvent(new OrganizationRegistered(organization.getId(), outputLanguage));

        return new RegistrationResult(
                organization.getId(),
                organization.getName(),
                admin.getId(),
                admin.getEmail(),
                admin.getDisplayName(),
                admin.getRole(),
                outputLanguage.tag(),
                organization.getSlug().value()
        );
    }

    /**
     * A public address made from the Organization's name. Whoever registers a name first
     * keeps the plain address and the next one is numbered, because two Organizations
     * cannot answer the same URL. An administrator can change it afterwards.
     */
    private OrganizationSlug availableSlug(String organizationName) {
        OrganizationSlug candidate = OrganizationSlug.fromName(organizationName);
        if (!organizationRepository.existsBySlug(candidate.value())) {
            return candidate;
        }
        for (int attempt = 2; attempt <= MAX_SLUG_ATTEMPTS; attempt++) {
            OrganizationSlug numbered = candidate.numbered(attempt);
            if (!organizationRepository.existsBySlug(numbered.value())) {
                return numbered;
            }
        }
        // A popular name, or a race: take one nobody is likely to hold and move on.
        return candidate.numbered(ThreadLocalRandom.current().nextInt(100_000, 1_000_000));
    }
}
