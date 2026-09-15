package com.hoangluongtran0309.releaseflow.translation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Translations of change content, one job per change, target language, and input. It
 * only reads and writes the database and never calls a provider, so it is safe inside
 * the transaction that approves a release: the worker picks new jobs up after commit.
 */
@Service
public class TranslationService {

    static final String DISABLED = "translation_disabled";

    private final TranslationJobRepository jobRepository;
    private final TranslationCacheRepository cacheRepository;
    private final TranslationProvider provider;
    private final Clock clock;

    TranslationService(
            TranslationJobRepository jobRepository,
            TranslationCacheRepository cacheRepository,
            TranslationProvider provider,
            Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.cacheRepository = cacheRepository;
        this.provider = provider;
        this.clock = clock;
    }

    public boolean isEnabled() {
        return provider.isEnabled();
    }

    /**
     * The change's texts in the target language, creating the job that translates them
     * when there is none yet. Texts already in the target language, or all blank, are
     * ready at once; texts all found in the cache make a job that has already succeeded.
     */
    @Transactional
    public TranslationState ensure(
            UUID organizationId,
            UUID projectId,
            UUID changeId,
            String sourceLanguage,
            String targetLanguage,
            Map<String, String> texts
    ) {
        Map<String, String> input = TranslationTexts.nonBlank(texts);
        if (input.isEmpty() || TranslationTexts.sameLanguage(sourceLanguage, targetLanguage)) {
            return TranslationState.ready(texts);
        }
        String hash = TranslationTexts.inputHash(sourceLanguage, input);
        TranslationJob job = jobRepository.findByChangeIdAndTargetLanguageAndInputHash(changeId, targetLanguage, hash)
                .orElseGet(() -> jobRepository.saveAndFlush(
                        newJob(organizationId, projectId, changeId, sourceLanguage, targetLanguage, hash, input)
                ));
        return merge(texts, job.state());
    }

    /**
     * Starts the failed translation of these texts over, then reports where it stands.
     * Anything but a failed job is left alone, and so is everything while no provider is
     * configured to translate.
     */
    @Transactional
    public TranslationState retry(
            UUID organizationId,
            UUID projectId,
            UUID changeId,
            String sourceLanguage,
            String targetLanguage,
            Map<String, String> texts
    ) {
        Map<String, String> input = TranslationTexts.nonBlank(texts);
        if (provider.isEnabled() && !input.isEmpty() && !TranslationTexts.sameLanguage(sourceLanguage, targetLanguage)) {
            jobRepository.findByChangeIdAndTargetLanguageAndInputHash(
                            changeId,
                            targetLanguage,
                            TranslationTexts.inputHash(sourceLanguage, input)
                    )
                    .filter(job -> job.getOrganizationId().equals(organizationId))
                    .ifPresent(job -> {
                        job.restart(now());
                        jobRepository.flush();
                    });
        }
        return ensure(organizationId, projectId, changeId, sourceLanguage, targetLanguage, texts);
    }

    private TranslationJob newJob(
            UUID organizationId,
            UUID projectId,
            UUID changeId,
            String sourceLanguage,
            String targetLanguage,
            String hash,
            Map<String, String> input
    ) {
        Instant now = now();
        TranslationJob job = new TranslationJob(
                organizationId, projectId, changeId, sourceLanguage, targetLanguage, hash, input, now
        );
        Optional<Map<String, String>> cached = cached(organizationId, sourceLanguage, targetLanguage, input);
        if (cached.isPresent()) {
            job.succeed(cached.get(), now);
        } else if (!provider.isEnabled()) {
            job.fail(DISABLED, now);
        }
        return job;
    }

    // Every text translated before, or nothing.
    private Optional<Map<String, String>> cached(
            UUID organizationId,
            String sourceLanguage,
            String targetLanguage,
            Map<String, String> input
    ) {
        Map<String, String> byHash = cacheRepository
                .findAllByOrganizationIdAndSourceLanguageAndTargetLanguageAndTextHashIn(
                        organizationId,
                        sourceLanguage,
                        targetLanguage,
                        input.values().stream().map(TranslationTexts::textHash).collect(Collectors.toSet())
                )
                .stream()
                .collect(Collectors.toMap(TranslationCacheEntry::getTextHash, TranslationCacheEntry::getTranslatedText));
        Map<String, String> translated = new LinkedHashMap<>();
        for (Map.Entry<String, String> text : input.entrySet()) {
            String hit = byHash.get(TranslationTexts.textHash(text.getValue()));
            if (hit == null) {
                return Optional.empty();
            }
            translated.put(text.getKey(), hit);
        }
        return Optional.of(translated);
    }

    // The given texts with each translated one replaced; blank texts stay as they were.
    private static TranslationState merge(Map<String, String> texts, TranslationState state) {
        if (!state.ready()) {
            return state;
        }
        Map<String, String> merged = new LinkedHashMap<>(texts);
        merged.replaceAll((key, value) -> state.texts().getOrDefault(key, value));
        return TranslationState.ready(merged);
    }

    // PostgreSQL keeps microseconds, so a time must survive a round trip unchanged.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
