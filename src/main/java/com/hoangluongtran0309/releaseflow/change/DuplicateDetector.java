package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.ChangedFile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares a change that just finished processing with earlier changes of its Project
 * and records the most similar ones as possible duplicates. The new change gets a
 * review trigger for each; earlier changes are left alone. It runs inside the
 * transaction that completes the change and reads only the database.
 */
@Component
class DuplicateDetector {

    static final Duration LOOKBACK = Duration.ofDays(180);
    static final int MAX_MATCHES = 5;

    private final ChangeRepository changeRepository;
    private final DuplicateCandidateRepository candidateRepository;
    private final ClassificationSettings settings;
    private final Clock clock;

    DuplicateDetector(
            ChangeRepository changeRepository,
            DuplicateCandidateRepository candidateRepository,
            ClassificationSettings settings,
            Clock clock
    ) {
        this.changeRepository = changeRepository;
        this.candidateRepository = candidateRepository;
        this.settings = settings;
        this.clock = clock;
    }

    void detect(Change change) {
        Instant now = clock.instant();
        // At most 500 of the Project's other processed changes from the last 180 days.
        List<Change> earlier = changeRepository
                .findTop500ByOrganizationIdAndProjectIdAndProcessingStatusAndIdNotAndReceivedAtAfterOrderByReceivedAtDescIdDesc(
                        change.getOrganizationId(),
                        change.getProjectId(),
                        ProcessingStatus.COMPLETED,
                        change.getId(),
                        now.minus(LOOKBACK)
                );
        DuplicateSimilarity.Subject subject = subject(change);
        List<Scored> matches = earlier.stream()
                .map(other -> new Scored(other, DuplicateSimilarity.compare(subject, subject(other))))
                .filter(scored -> scored.match().score() >= settings.duplicateThreshold())
                .sorted(Comparator.comparingDouble((Scored scored) -> scored.match().score()).reversed())
                .limit(MAX_MATCHES)
                .filter(scored -> !candidateRepository.existsByChangeIdAndDuplicateOfId(change.getId(), scored.other().getId()))
                .toList();
        if (matches.isEmpty()) {
            return;
        }
        candidateRepository.saveAll(matches.stream()
                .map(scored -> new DuplicateCandidate(
                        change,
                        scored.other(),
                        Math.min(1, scored.match().score()),
                        new DuplicateEvidence(
                                round(scored.match().title()),
                                round(scored.match().content()),
                                round(scored.match().paths())
                        ),
                        now
                ))
                .toList());
        change.addReviewTriggers(matches.stream()
                .map(scored -> ReviewTrigger.duplicateCandidate(scored.other().getId()))
                .toList());
    }

    private static DuplicateSimilarity.Subject subject(Change change) {
        NeutralSummary summary = change.getNeutralSummary();
        String description = change.getDescription() == null ? "" : change.getDescription();
        Set<String> paths = new HashSet<>();
        for (ChangedFile file : change.getChangedFiles()) {
            paths.add(file.path());
            if (file.previousPath() != null) {
                paths.add(file.previousPath());
            }
        }
        return new DuplicateSimilarity.Subject(
                change.getTitle(),
                summary == null ? description : description + " " + summary.whatChanged(),
                paths
        );
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    private record Scored(Change other, DuplicateSimilarity.Match match) {
    }
}
