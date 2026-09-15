package com.hoangluongtran0309.releaseflow.account;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class RegistrationService {

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
        Organization organization = new Organization(
                UUID.randomUUID(),
                request.getOrganizationName().strip(),
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
                outputLanguage.tag()
        );
    }
}
