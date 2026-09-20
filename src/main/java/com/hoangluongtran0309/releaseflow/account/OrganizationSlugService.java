package com.hoangluongtran0309.releaseflow.account;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The address an Organization's public changelog answers on. Changing it moves the
 * changelog: links that were shared under the old address stop working, which is why
 * only an administrator may do it.
 */
@Service
public class OrganizationSlugService {

    private static final String SLUG_CONSTRAINT = "organizations_slug_unique";

    private final OrganizationRepository organizationRepository;

    OrganizationSlugService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Transactional(readOnly = true)
    public OrganizationSlug slug(UUID organizationId) {
        return find(organizationId).getSlug();
    }

    /** The Organization that answers this address, for a public reader who has only it. */
    @Transactional(readOnly = true)
    public Optional<PublicOrganization> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return organizationRepository.findBySlug(slug.strip().toLowerCase(java.util.Locale.ROOT))
                .map(organization -> new PublicOrganization(
                        organization.getId(), organization.getName(), organization.getSlug().value()));
    }

    /** The Organization itself, for a page or an entry that must snapshot how it reads. */
    @Transactional(readOnly = true)
    public PublicOrganization publicOrganization(UUID organizationId) {
        Organization organization = find(organizationId);
        return new PublicOrganization(organization.getId(), organization.getName(), organization.getSlug().value());
    }

    @Transactional
    public OrganizationSlug change(UUID organizationId, String value) {
        OrganizationSlug slug = OrganizationSlug.parse(value);
        Organization organization = find(organizationId);
        if (organization.getSlug().equals(slug)) {
            return slug;
        }
        if (organizationRepository.existsBySlug(slug.value())) {
            throw new OrganizationSlugTakenException(slug.value());
        }
        organization.changeSlug(slug);
        try {
            organizationRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            // Somebody took it between the check and the write.
            throw violates(exception, SLUG_CONSTRAINT)
                    ? new OrganizationSlugTakenException(slug.value())
                    : exception;
        }
        return slug;
    }

    private Organization find(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalStateException("Organization " + organizationId + " does not exist."));
    }

    private static boolean violates(DataIntegrityViolationException exception, String constraint) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    /** What a public page may know about an Organization: no identifiers beyond these. */
    public record PublicOrganization(UUID id, String name, String slug) {
    }
}
