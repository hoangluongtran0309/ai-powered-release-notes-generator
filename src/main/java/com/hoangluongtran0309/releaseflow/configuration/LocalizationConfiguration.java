package com.hoangluongtran0309.releaseflow.configuration;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;

@Configuration
class LocalizationConfiguration {

    /** Named {@code localeResolver}, which is what Spring MVC looks the resolver up by. */
    @Bean
    LocaleResolver localeResolver(UiLanguages languages) {
        return new UiLocaleResolver(languages);
    }

    /**
     * Reads the same bundle as the rest of the UI, but answers a missing key with nothing
     * rather than with the key itself. Bean Validation then falls back to its own wording
     * for a constraint ReleaseFlow has not given a message, instead of printing a key.
     */
    @Bean
    MessageSource validationMessageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages/ui");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    /**
     * Replaces Spring Boot's own validator so that a constraint message written as
     * {@code {validation.some.key}} is read from the bundle in the language of the request
     * being answered.
     */
    @Bean
    LocalValidatorFactoryBean defaultValidator(MessageSource validationMessageSource) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(validationMessageSource);
        return validator;
    }
}
