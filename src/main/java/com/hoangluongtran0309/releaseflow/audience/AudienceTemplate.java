package com.hoangluongtran0309.releaseflow.audience;

import com.samskivert.mustache.Escapers;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;


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
    private static final String NARRATIVES = "narratives";
    private static final int MESSAGE_LIMIT = 300;

    private AudienceTemplate() {
    }

    /**
     * Whether the template reaches for another audience's narrative — {{narratives.x}}
     * in any of its Mustache spellings, with or without the {@code &} or the third
     * brace, and with spaces anywhere inside the tag.
     *
     * <p>It is a walk rather than a pattern. An expression that has to allow a run of
     * braces and spaces before the word is retried from every brace in that run, so a
     * template made of nothing else would cost time in proportion to the square of its
     * length. Looking for the word first, and only then reading outwards from it, costs
     * one pass and a little more.
     */
    private static boolean namesAnotherAudience(String body) {
        for (int at = body.indexOf(NARRATIVES); at >= 0; at = body.indexOf(NARRATIVES, at + 1)) {
            if (opensATag(body, at) && startsAPath(body, at + NARRATIVES.length())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a Mustache tag opens just before here: two braces, then spaces, then at
     * most one {@code &} or third brace, then spaces. The sigil is optional in both
     * directions — in {@code {{narratives.x}}} the brace before the word is the tag's
     * own, and in {@code {{{narratives.x}}} it is the sigil — so both readings are tried.
     */
    private static boolean opensATag(String body, int before) {
        int at = skipSpacesBack(body, before);
        if (twoBracesBefore(body, at)) {
            return true;
        }
        if (at > 0 && (body.charAt(at - 1) == '&' || body.charAt(at - 1) == '{')) {
            return twoBracesBefore(body, skipSpacesBack(body, at - 1));
        }
        return false;
    }

    private static boolean twoBracesBefore(String body, int at) {
        return at >= 2 && body.charAt(at - 1) == '{' && body.charAt(at - 2) == '{';
    }

    /** Whether a dot, and so a path into the narratives of every audience, comes next. */
    private static boolean startsAPath(String body, int from) {
        int at = from;
        while (at < body.length() && Character.isWhitespace(body.charAt(at))) {
            at++;
        }
        return at < body.length() && body.charAt(at) == '.';
    }

    private static int skipSpacesBack(String body, int from) {
        int at = from;
        while (at > 0 && Character.isWhitespace(body.charAt(at - 1))) {
            at--;
        }
        return at;
    }

    /**
     * Rejects a template that is blank, too long, names another audience's narrative, or
     * does not compile and render with sample values.
     */
    public static void validate(String body) {
        if (body == null || body.isBlank()) {
            throw new InvalidAudienceTemplateException(
                    InvalidAudienceTemplateException.INVALID, "error.template_invalid.required");
        }
        if (body.length() > MAX_LENGTH) {
            throw new InvalidAudienceTemplateException(
                    InvalidAudienceTemplateException.INVALID,
                    "error.template_invalid.tooLong", MAX_LENGTH
            );
        }
        if (namesAnotherAudience(body)) {
            throw new InvalidAudienceTemplateException(
                    InvalidAudienceTemplateException.NARRATIVES_PATH,
                    "error.template_narratives_path"
            );
        }
        try {
            COMPILER.compile(body).execute(AudienceItem.SAMPLE.context());
        } catch (MustacheException | IllegalArgumentException exception) {
            throw new InvalidAudienceTemplateException(
                    InvalidAudienceTemplateException.INVALID,
                    "error.template_invalid.mustache", describe(exception)
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
