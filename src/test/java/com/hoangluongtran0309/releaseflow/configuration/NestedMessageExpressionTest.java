package com.hoangluongtran0309.releaseflow.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.messageresolver.SpringMessageResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two pages name a fixed constant inside a sentence — "{0} group" filled with the name of
 * a category group — which is a message expression used as the argument of another. The
 * construct is rare enough to be worth proving rather than assuming, since a template that
 * failed to parse would only show up on a page nothing renders without an AI suggestion.
 */
class NestedMessageExpressionTest {

    private static final TemplateEngine ENGINE = engine();

    @Test
    void aMessageCanBeBuiltFromAKeyHeldInAVariable() {
        Context context = new Context(Locale.ENGLISH, Map.of("labelKey", "ui.enum.categoryGroup.FIX"));

        assertThat(render("<p th:text=\"#{${labelKey}}\">x</p>", context)).isEqualTo("<p>Fix</p>");
    }

    @Test
    void aMessageCanTakeAnotherMessageAsItsArgument() {
        Context english = new Context(Locale.ENGLISH, Map.of("labelKey", "ui.enum.categoryGroup.FIX"));
        Context vietnamese = new Context(Locale.forLanguageTag("vi"), Map.of("labelKey", "ui.enum.categoryGroup.FIX"));
        String template = "<p th:text=\"#{ui.categories.groupSuffix(#{${labelKey}})}\">x</p>";

        assertThat(render(template, english)).isEqualTo("<p>Fix group</p>");
        assertThat(render(template, vietnamese)).isEqualTo("<p>nhóm Sửa lỗi</p>");
    }

    private static String render(String template, Context context) {
        Locale previous = LocaleContextHolder.getLocale();
        try {
            LocaleContextHolder.setLocale(context.getLocale());
            return ENGINE.process(template, context).strip();
        } finally {
            LocaleContextHolder.setLocale(previous);
        }
    }

    private static TemplateEngine engine() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages/ui");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        SpringMessageResolver messageResolver = new SpringMessageResolver();
        messageResolver.setMessageSource(messages);
        engine.setMessageResolver(messageResolver);
        return engine;
    }
}
