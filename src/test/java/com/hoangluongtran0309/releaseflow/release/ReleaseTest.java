package com.hoangluongtran0309.releaseflow.release;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseTest {

    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");
    private static final UUID PERSON = UUID.randomUUID();

    @Test
    void movesFromDraftThroughReviewAndApprovalToPublication() {
        Release release = draft();

        release.requestReview(NOW);
        assertThat(release.getStatus()).isEqualTo(ReleaseStatus.IN_REVIEW);
        release.approve(PERSON, "Mai Tran", NOW.plusSeconds(60));
        assertThat(release.getStatus()).isEqualTo(ReleaseStatus.APPROVED);
        assertThat(release.getApproverName()).isEqualTo("Mai Tran");
        assertThat(release.getApprovedAt()).isEqualTo(NOW.plusSeconds(60));
        release.publish(PERSON, "Mai Tran", NOW.plusSeconds(120));
        assertThat(release.getStatus()).isEqualTo(ReleaseStatus.PUBLISHED);
        assertThat(release.getPublishedAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(release.getUpdatedAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void refusesTransitionsOutOfOrder() {
        Release release = draft();
        assertThatThrownBy(() -> release.approve(PERSON, "Mai Tran", NOW)).isInstanceOf(ReleaseStatusException.class);
        assertThatThrownBy(() -> release.publish(PERSON, "Mai Tran", NOW)).isInstanceOf(ReleaseStatusException.class);
        assertThatThrownBy(() -> release.returnToDraft(NOW)).isInstanceOf(ReleaseStatusException.class);
        assertThatThrownBy(release::requireInReview).isInstanceOf(ReleaseStatusException.class);

        release.requestReview(NOW);
        assertThatThrownBy(() -> release.requestReview(NOW)).isInstanceOf(ReleaseStatusException.class);
        assertThatThrownBy(() -> release.publish(PERSON, "Mai Tran", NOW)).isInstanceOf(ReleaseStatusException.class);
        assertThatThrownBy(() -> release.edit("2.0.0", null, NOW)).isInstanceOf(ReleaseStatusException.class);
        assertThatCode(release::requireChangesRemovable).doesNotThrowAnyException();

        release.approve(PERSON, "Mai Tran", NOW);
        assertThatThrownBy(release::requireChangesRemovable).isInstanceOf(ReleaseStatusException.class);
        assertThatThrownBy(release::requireInReview).isInstanceOf(ReleaseStatusException.class);
    }

    @Test
    void returnsToDraftAndForgetsTheApproval() {
        Release release = draft();
        release.requestReview(NOW);
        release.returnToDraft(NOW);
        assertThat(release.getStatus()).isEqualTo(ReleaseStatus.DRAFT);

        release.requestReview(NOW);
        release.approve(PERSON, "Mai Tran", NOW);
        release.returnToDraft(NOW.plusSeconds(5));
        assertThat(release.getStatus()).isEqualTo(ReleaseStatus.DRAFT);
        assertThat(release.getApprovedAt()).isNull();
        assertThat(release.getApproverName()).isNull();
        assertThatCode(() -> release.edit("1.4.1", "Fixed.", NOW)).doesNotThrowAnyException();
    }

    @Test
    void freezesEverythingOncePublished() {
        Release release = draft();
        release.requestReview(NOW);
        release.approve(PERSON, "Mai Tran", NOW);
        release.publish(PERSON, "Mai Tran", NOW);

        assertThatThrownBy(() -> release.edit("2.0.0", null, NOW)).isInstanceOf(ReleasePublishedException.class);
        assertThatThrownBy(() -> release.requestReview(NOW)).isInstanceOf(ReleasePublishedException.class);
        assertThatThrownBy(() -> release.approve(PERSON, "Mai Tran", NOW)).isInstanceOf(ReleasePublishedException.class);
        assertThatThrownBy(() -> release.returnToDraft(NOW)).isInstanceOf(ReleasePublishedException.class);
        assertThatThrownBy(() -> release.publish(PERSON, "Mai Tran", NOW)).isInstanceOf(ReleasePublishedException.class);
        assertThatThrownBy(() -> release.schedule(null, NOW)).isInstanceOf(ReleasePublishedException.class);
        assertThatThrownBy(release::requireChangesRemovable).isInstanceOf(ReleasePublishedException.class);
        assertThatThrownBy(release::requireUnpublished).isInstanceOf(ReleasePublishedException.class);
    }

    @Test
    void schedulesOnlyInTheFuture() {
        Release release = draft();

        release.schedule(NOW.plusSeconds(1), NOW);
        assertThat(release.getPlannedReleaseAt()).isEqualTo(NOW.plusSeconds(1));
        assertThatThrownBy(() -> release.schedule(NOW, NOW))
                .isInstanceOf(InvalidReleaseScheduleException.class)
                .hasMessage("error.invalid_release_schedule.past");
        assertThatThrownBy(() -> release.schedule(NOW.minusSeconds(1), NOW)).isInstanceOf(InvalidReleaseScheduleException.class);
        assertThat(release.getPlannedReleaseAt()).isEqualTo(NOW.plusSeconds(1));

        release.requestReview(NOW);
        release.approve(PERSON, "Mai Tran", NOW);
        release.schedule(null, NOW);
        assertThat(release.getPlannedReleaseAt()).isNull();
    }

    private static Release draft() {
        return new Release(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "1.4.0", null, NOW);
    }
}
