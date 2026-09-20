package com.hoangluongtran0309.releaseflow.audience;

import com.samskivert.mustache.Escapers;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An audience's Mustache template, which renders one change as Markdown. Rendering is
 * pure: it never calls AI or anything else outside the process. Values are inserted
 * as they are, because the output is Markdown rather than HTML; the Markdown preview
 * escapes HTML when it is displayed.
 */
public final class AudienceTemplate {

    public static final int MAX_LENGTH = 10000;

    private static final Mustache.Compiler COMPILER = Mustache.compiler()
            .standardsMode(true)
            .strictSections(true)
            .emptyStringIsFalse(true)
            .withEscaper(Escapers.NONE);
    // One character class rather than two runs of whitespace around an optional sigil:
    // with two, a long run of spaces can be split between them in many ways and the
    // scan costs time proportional to the square of the template's length.
    private static final Pattern NARRATIVES_PATH = Pattern.compile("\\{\\{[\\s&{]*narratives\\s*\\.");
    private static final int MESSAGE_LIMIT = 300;

    private AudienceTemplate() {
    }

    /**
     * Rejects a template that is blank, too long, names another audience's narrative, or
     * does not compile and render with sample values.
     */
    public static void validate(String body) {
        if (body == null || body.isBlank()) {
            throw new InvalidAudienceTemplateException(InvalidAudienceTemplateException.INVALID, "A template is required.");
        }
        if (body.length() > MAX_LENGTH) {
            throw new InvalidAudienceTemplateException(
                    InvalidAudienceTemplateException.INVALID,
                    "A template must not exceed " + MAX_LENGTH + " characters."
            );
        }
        Matcher narratives = NARRATIVES_PATH.matcher(body);
        if (narratives.find()) {
            throw new InvalidAudienceTemplateException(
                    InvalidAudienceTemplateException.NARRATIVES_PATH,
                    "Use {{narrative}}. Each audience gets its own narrative, so a template never names an audience."
            );
        }
        try {
            COMPILER.compile(body).execute(AudienceItem.SAMPLE.context());
        } catch (MustacheException | IllegalArgumentException exception) {
            throw new InvalidAudienceTemplateException(
                    InvalidAudienceTemplateException.INVALID,
                    "This template is not valid Mustache: " + describe(exception)
            );
        }
    }

    /**
     * Renders one change. A template that was validated when it was saved renders every
     * item, because every variable always has a value.
     *
     * @throws AudienceTemplateRenderException if the template cannot be rendered
     */
    public static String render(String body, AudienceItem item) {
        try {
            return COMPILER.compile(body).execute(item.context());
        } catch (MustacheException | IllegalArgumentException exception) {
            throw new AudienceTemplateRenderException(exception);
        }
    }

    /** The template rendered with sample values, as shown by the editor's preview. */
    public static String sample(String body) {
        validate(body);
        return render(body, AudienceItem.SAMPLE);
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage() == null ? "it cannot be parsed." : exception.getMessage().strip();
        return message.length() <= MESSAGE_LIMIT ? message : message.substring(0, MESSAGE_LIMIT - 1) + "…";
    }
}
