package com.hoangluongtran0309.releaseflow.translation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Translates due jobs. A claim and a result are each written in a short transaction; the
 * provider is called between them, only for texts not already in the cache. Transient
 * failures are retried with backoff, at most {@value #MAX_ATTEMPTS} times in all.
 */
@Component
class TranslationWorker {

    static final int MAX_ATTEMPTS = 5;
    static final Duration STALE_AFTER = Duration.ofMinutes(10);
    static final String UNEXPECTED_ERROR = "unexpected_error";

    private static final Logger log = LoggerFactory.getLogger(TranslationWorker.class);

    private final TranslationJobRepository jobRepository;
    private final TranslationCacheRepository cacheRepository;
    private final TranslationProvider provider;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final boolean enabled;

    TranslationWorker(
            TranslationJobRepository jobRepository,
            TranslationCacheRepository cacheRepository,
            TranslationProvider provider,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${releaseflow.translation.worker-enabled}") boolean enabled
    ) {
        this.jobRepository = jobRepository;
        this.cacheRepository = cacheRepository;
        this.provider = provider;
        this.events = events;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "PT1S")
    void processDueJobs() {
        if (!enabled) {
            return;
        }
        try {
            while (processOne()) {
                // Keep draining due jobs before sleeping again.
            }
        } catch (RuntimeException exception) {
            log.error("Translation stopped unexpectedly; it resumes on the next tick.", exception);
        }
    }

    /**
     * Works at most one due job.
     *
     * @return whether a job was claimed
     */
    boolean processOne() {
        Instant now = now();
        transactionTemplate.executeWithoutResult(status -> {
            int recovered = jobRepository.recoverStale(now.minus(STALE_AFTER), now);
            if (recovered > 0) {
                log.warn("Recovered {} translation job(s) whose worker stopped responding.", recovered);
            }
        });
        Optional<Claim> claim = transactionTemplate.execute(status -> jobRepository.lockNextDue(now).map(job -> {
            job.claim(now);
            return new Claim(job.getId(), job.getOrganizationId(), job.getSourceLanguage(), job.getTargetLanguage(),
                    job.getInput(), cached(job), job.getAttempts(), now);
        }));
        if (claim.isEmpty()) {
            return false;
        }
        work(claim.get());
        return true;
    }

    private void work(Claim claim) {
        Optional<Translator> translator = provider.active();
        if (translator.isEmpty()) {
            finish(claim, job -> job.fail(TranslationService.DISABLED, now()), true);
            return;
        }
        List<String> missing = new ArrayList<>(new LinkedHashSet<>(claim.input().values().stream()
                .filter(text -> !claim.cached().containsKey(text))
                .toList()));
        Map<String, String> fresh = new HashMap<>();
        try {
            if (!missing.isEmpty()) {
                List<String> translated = translator.get().translate(missing, claim.sourceLanguage(), claim.targetLanguage());
                for (int index = 0; index < missing.size(); index++) {
                    fresh.put(missing.get(index), translated.get(index));
                }
            }
        } catch (TranslationException exception) {
            failOrRetry(claim, exception.code(), exception.retryable());
            return;
        } catch (RuntimeException exception) {
            log.error("Translation job {} failed unexpectedly.", claim.jobId(), exception);
            failOrRetry(claim, UNEXPECTED_ERROR, true);
            return;
        }

        Map<String, String> output = new LinkedHashMap<>();
        claim.input().forEach((key, text) -> output.put(key, claim.cached().getOrDefault(text, fresh.get(text))));
        Instant at = now();
        finish(claim, job -> {
            job.succeed(output, at);
            fresh.forEach((text, translation) -> {
                TranslationCacheEntry.Key key = new TranslationCacheEntry.Key(
                        claim.organizationId(), claim.sourceLanguage(), claim.targetLanguage(), TranslationTexts.textHash(text)
                );
                if (!cacheRepository.existsById(key)) {
                    cacheRepository.save(new TranslationCacheEntry(key, translation, at));
                }
            });
        }, true);
        log.info("Translated job {} into {} ({} text(s), {} from the cache).", claim.jobId(), claim.targetLanguage(),
                claim.input().size(), claim.input().size() - fresh.size());
    }

    private void failOrRetry(Claim claim, String error, boolean retryable) {
        if (retryable && claim.attempt() < MAX_ATTEMPTS) {
            Instant retryAt = now().plus(backoff(claim.attempt()));
            finish(claim, job -> job.retryLater(error, retryAt), false);
            log.info("Retrying translation job {} at {} after attempt {} ({}).", claim.jobId(), retryAt, claim.attempt(), error);
        } else {
            finish(claim, job -> job.fail(error, now()), true);
            log.warn("Translation job {} failed after attempt {} ({}).", claim.jobId(), claim.attempt(), error);
        }
    }

    // Applies the result if this worker still holds the claim, and tells listeners when it is final.
    private void finish(Claim claim, Consumer<TranslationJob> result, boolean finished) {
        transactionTemplate.executeWithoutResult(status -> jobRepository.findById(claim.jobId())
                .filter(job -> job.isClaimedAt(claim.claimedAt()))
                .ifPresent(job -> {
                    result.accept(job);
                    jobRepository.flush();
                    if (finished) {
                        events.publishEvent(new TranslationFinished(job.getOrganizationId(), job.getChangeId(),
                                job.getTargetLanguage()));
                    }
                }));
    }

    // Text -> translation for every input text already in the cache.
    private Map<String, String> cached(TranslationJob job) {
        Map<String, String> byHash = job.getInput().values().stream()
                .distinct()
                .collect(Collectors.toMap(TranslationTexts::textHash, text -> text));
        Map<String, String> cached = new HashMap<>();
        cacheRepository.findAllByOrganizationIdAndSourceLanguageAndTargetLanguageAndTextHashIn(
                        job.getOrganizationId(), job.getSourceLanguage(), job.getTargetLanguage(), byHash.keySet())
                .forEach(entry -> cached.put(byHash.get(entry.getTextHash()), entry.getTranslatedText()));
        return cached;
    }

    // 2, 4, 8 … seconds, capped at five minutes.
    static Duration backoff(int attempt) {
        return Duration.ofSeconds(Math.min(300, 1L << Math.min(8, attempt)));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private record Claim(
            UUID jobId,
            UUID organizationId,
            String sourceLanguage,
            String targetLanguage,
            Map<String, String> input,
            Map<String, String> cached,
            int attempt,
            Instant claimedAt
    ) {
    }
}
