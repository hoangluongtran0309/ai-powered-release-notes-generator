package com.hoangluongtran0309.releaseflow.category;

import com.hoangluongtran0309.releaseflow.account.OrganizationRegistered;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * An Organization's category catalog. The fixed rules, the AI, and reviewers choose
 * only among its active categories; changes keep a snapshot of the category they got.
 */
@Service
public class CategoryService {

    /** The catalog every Organization starts with; V14 seeds the same rows. */
    static final List<CategoryRef> DEFAULTS = List.of(
            new CategoryRef("FEATURE", "Feature", CategoryGroup.FEATURE),
            new CategoryRef("FIX", "Fix", CategoryGroup.FIX),
            new CategoryRef("PERFORMANCE", "Performance", CategoryGroup.PERFORMANCE),
            new CategoryRef("DOCUMENTATION", "Documentation", CategoryGroup.DOCUMENTATION),
            new CategoryRef("MAINTENANCE", "Maintenance", CategoryGroup.MAINTENANCE),
            CategoryRef.UNKNOWN
    );

    private static final String CODE_CONSTRAINT = "category_definitions_code_unique";

    private final CategoryRepository categoryRepository;
    private final Clock clock;

    CategoryService(CategoryRepository categoryRepository, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    /** Every category, archived ones included, ordered by code. */
    @Transactional(readOnly = true)
    public List<CategoryView> list(UUID organizationId) {
        return categoryRepository.findAllByOrganizationIdOrderByCodeAsc(organizationId).stream()
                .map(CategoryDefinition::view)
                .toList();
    }

    /** The active categories, ordered by code. Unknown is always among them. */
    @Transactional(readOnly = true)
    public List<CategoryRef> active(UUID organizationId) {
        return categoryRepository.findAllByOrganizationIdAndActiveTrueOrderByCodeAsc(organizationId).stream()
                .map(CategoryDefinition::ref)
                .toList();
    }

    /** An active category by code, ignoring case. */
    @Transactional(readOnly = true)
    public Optional<CategoryRef> find(UUID organizationId, String code) {
        return CategoryRef.normalize(code)
                .flatMap(normalized -> categoryRepository.findByOrganizationIdAndCode(organizationId, normalized))
                .filter(CategoryDefinition::isActive)
                .map(CategoryDefinition::ref);
    }

    @Transactional
    public CategoryView create(UUID organizationId, NewCategoryRequest request) {
        return create(organizationId, request.getCode(), request.getDisplayName(), request.getGroup()).view();
    }

    @Transactional
    public CategoryView update(UUID organizationId, UUID categoryId, CategoryRequest request) {
        CategoryDefinition category = get(organizationId, categoryId);
        category.update(request.getDisplayName(), request.getGroup(), clock.instant());
        categoryRepository.flush();
        return category.view();
    }

    @Transactional
    public CategoryView archive(UUID organizationId, UUID categoryId) {
        CategoryDefinition category = get(organizationId, categoryId);
        category.archive(clock.instant());
        categoryRepository.flush();
        return category.view();
    }

    @Transactional
    public CategoryView unarchive(UUID organizationId, UUID categoryId) {
        CategoryDefinition category = get(organizationId, categoryId);
        category.unarchive(clock.instant());
        categoryRepository.flush();
        return category.view();
    }

    // Runs inside the registration transaction; a failure rolls the registration back.
    @EventListener
    void seed(OrganizationRegistered event) {
        Instant now = clock.instant();
        categoryRepository.saveAll(DEFAULTS.stream()
                .map(category -> new CategoryDefinition(
                        UUID.randomUUID(),
                        event.organizationId(),
                        category.code(),
                        category.displayName(),
                        category.group(),
                        category.isUnknown(),
                        now
                ))
                .toList());
    }

    /**
     * The category an approved suggestion names: an existing one with its code, restored
     * if it was archived, or a new one.
     */
    CategoryDefinition activateForSuggestion(UUID organizationId, String code, String displayName, CategoryGroup group) {
        Optional<CategoryDefinition> existing = categoryRepository.findByOrganizationIdAndCode(organizationId, code);
        if (existing.isPresent()) {
            if (!existing.get().isActive()) {
                existing.get().unarchive(clock.instant());
            }
            return existing.get();
        }
        return create(organizationId, code, displayName, group);
    }

    CategoryDefinition get(UUID organizationId, UUID categoryId) {
        return categoryRepository.findByIdAndOrganizationId(categoryId, organizationId)
                .orElseThrow(CategoryNotFoundException::new);
    }

    private CategoryDefinition create(UUID organizationId, String code, String displayName, CategoryGroup group) {
        if (categoryRepository.findByOrganizationIdAndCode(organizationId, code).isPresent()) {
            throw CategoryConflictException.codeTaken();
        }
        CategoryDefinition category = new CategoryDefinition(
                UUID.randomUUID(),
                organizationId,
                code,
                displayName,
                group,
                false,
                clock.instant()
        );
        try {
            categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException exception) {
            throw violates(exception) ? CategoryConflictException.codeTaken() : exception;
        }
        return category;
    }

    private static boolean violates(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(CODE_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }
}
