package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.WebhookAuthMode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Reports the fields the chosen provider needs against those fields, so REST callers get
 * them in the {@code errors} map and the Projects page renders them under their inputs.
 */
public class IntegrationSourceRequestValidator
        implements ConstraintValidator<ValidIntegrationSourceRequest, IntegrationSourceRequest> {

    private static final Pattern GITHUB_SEGMENT = Pattern.compile("[A-Za-z0-9_.-]+");
    // A GitLab project path is a namespace and a project, with any number of subgroups.
    private static final Pattern GITLAB_PROJECT_PATH = Pattern.compile("[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)+");

    @Override
    public boolean isValid(IntegrationSourceRequest request, ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();
        return switch (request.getType()) {
            case GITHUB -> gitHub(request, context);
            case GITLAB -> gitLab(request, context);
        };
    }

    private static boolean gitHub(IntegrationSourceRequest request, ConstraintValidatorContext context) {
        boolean valid = segment(request.getOwner(), 39, "owner", "Repository owner", context);
        valid &= segment(request.getRepository(), 100, "repository", "Repository name", context);
        return valid;
    }

    private static boolean gitLab(IntegrationSourceRequest request, ConstraintValidatorContext context) {
        boolean valid = true;
        String projectPath = request.getProjectPath();
        if (projectPath == null || projectPath.isBlank()) {
            valid = reject(context, "projectPath", "Project path is required.");
        } else if (projectPath.length() > 200) {
            valid = reject(context, "projectPath", "Project path must not exceed 200 characters.");
        } else if (!GITLAB_PROJECT_PATH.matcher(projectPath).matches()) {
            valid = reject(context, "projectPath", "Project path must look like group/project.");
        }
        String baseUrl = request.getApiBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            valid = reject(context, "apiBaseUrl", "GitLab instance URL is required.");
        } else if (baseUrl.length() > 255) {
            valid = reject(context, "apiBaseUrl", "GitLab instance URL must not exceed 255 characters.");
        }
        WebhookAuthMode mode = request.getWebhookAuthMode();
        if (mode != WebhookAuthMode.GITLAB_SIGNING_TOKEN && mode != WebhookAuthMode.GITLAB_SECRET_TOKEN) {
            valid = reject(context, "webhookAuthMode", "Choose how GitLab will prove its deliveries.");
        }
        return valid;
    }

    private static boolean segment(
            String value,
            int maxLength,
            String property,
            String label,
            ConstraintValidatorContext context
    ) {
        if (value == null || value.isBlank()) {
            return reject(context, property, label + " is required.");
        }
        if (value.length() > maxLength) {
            return reject(context, property, label + " must not exceed " + maxLength + " characters.");
        }
        if (!GITHUB_SEGMENT.matcher(value).matches()) {
            return reject(context, property, label + " contains unsupported characters.");
        }
        return true;
    }

    private static boolean reject(ConstraintValidatorContext context, String property, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
        return false;
    }
}
