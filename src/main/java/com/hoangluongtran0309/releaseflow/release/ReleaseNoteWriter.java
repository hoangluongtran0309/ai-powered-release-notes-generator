package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.change.ChangeView;
import com.hoangluongtran0309.releaseflow.change.NeutralSummary;
import com.hoangluongtran0309.releaseflow.translation.TranslationService;
import com.hoangluongtran0309.releaseflow.translation.TranslationState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Puts a release's changes into one note language. A change whose summary is in another
 * language uses its translation once that is ready, and its own text until then; the
 * note is only as ready as its least ready change. Only the database is touched, so this
 * is safe inside the transaction that approves a release.
 */
@Component
class ReleaseNoteWriter {

    static final String WHAT_CHANGED = "whatChanged";
    static final String WHY_CHANGED = "whyChanged";
    static final String TECHNICAL_DETAIL = "technicalDetail";
    static final String MIGRATION_STEP = "migrationStep";
    static final String NARRATIVE = "narrative.";

    private final TranslationService translationService;

    ReleaseNoteWriter(TranslationService translationService) {
        this.translationService = translationService;
    }

    /**
     * @param retry whether to start failed translations over first
     */
    Localized localize(Release release, List<ChangeView> changes, String language, boolean retry) {
        List<ChangeView> localized = new ArrayList<>(changes.size());
        TranslationState.Status status = TranslationState.Status.READY;
        for (ChangeView change : changes) {
            NeutralSummary summary = change.neutralSummary();
            if (summary == null || change.contentLanguage() == null) {
                // Without a summary a change reads as its pull request title, which is not translated.
                localized.add(change);
                continue;
            }
            Map<String, String> texts = texts(change);
            TranslationState state = retry
                    ? translationService.retry(release.getOrganizationId(), release.getProjectId(), change.id(),
                    change.contentLanguage(), language, texts)
                    : translationService.ensure(release.getOrganizationId(), release.getProjectId(), change.id(),
                    change.contentLanguage(), language, texts);
            if (state.ready()) {
                localized.add(translated(change, state.texts(), language));
            } else {
                localized.add(change);
                status = worse(status, state.status());
            }
        }
        return new Localized(localized, status);
    }

    /** The texts of a change that are translated: its summary fields and each audience's narrative. */
    static Map<String, String> texts(ChangeView change) {
        NeutralSummary summary = change.neutralSummary();
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put(WHAT_CHANGED, summary.whatChanged());
        texts.put(WHY_CHANGED, summary.whyChanged());
        texts.put(TECHNICAL_DETAIL, summary.technicalDetail());
        texts.put(MIGRATION_STEP, summary.migrationStep());
        change.audienceNarratives().forEach((code, narrative) -> texts.put(NARRATIVE + code, narrative == null ? "" : narrative));
        return texts;
    }

    static ChangeView translated(ChangeView change, Map<String, String> texts, String language) {
        NeutralSummary summary = new NeutralSummary(
                texts.get(WHAT_CHANGED),
                texts.get(WHY_CHANGED),
                texts.get(TECHNICAL_DETAIL),
                texts.get(MIGRATION_STEP)
        );
        Map<String, String> narratives = new LinkedHashMap<>();
        texts.forEach((key, text) -> {
            if (key.startsWith(NARRATIVE)) {
                narratives.put(key.substring(NARRATIVE.length()), text);
            }
        });
        return change.withContent(summary, narratives, language);
    }

    // A failure needs a person, so it outranks a translation still on its way.
    static TranslationState.Status worse(TranslationState.Status first, TranslationState.Status second) {
        if (first == TranslationState.Status.FAILED || second == TranslationState.Status.FAILED) {
            return TranslationState.Status.FAILED;
        }
        if (first == TranslationState.Status.PENDING || second == TranslationState.Status.PENDING) {
            return TranslationState.Status.PENDING;
        }
        return TranslationState.Status.READY;
    }

    /** A release's changes in one note language, and how ready that language is. */
    record Localized(List<ChangeView> changes, TranslationState.Status status) {
    }
}
