package com.hoangluongtran0309.releaseflow.overview;

import com.hoangluongtran0309.releaseflow.change.ChangeCounts;
import com.hoangluongtran0309.releaseflow.release.ReleaseCounts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The order is the whole point: the overview offers one starting point, never a list. */
class NextStepTest {

    private static final ChangeCounts NO_CHANGES = ChangeCounts.NONE;
    private static final ChangeCounts SOME_CHANGES = new ChangeCounts(7, 2, 1);

    @Test
    void setupComesBeforeAnythingThatCouldBeDoneWithIt() {
        assertThat(NextStep.of(false, false, NO_CHANGES, ReleaseCounts.NONE)).isEqualTo(NextStep.CREATE_PROJECT);
        assertThat(NextStep.of(true, false, NO_CHANGES, ReleaseCounts.NONE)).isEqualTo(NextStep.CONNECT_SOURCE);
    }

    @Test
    void aWorkspaceWithNoChangesIsWaiting_evenWithADraftOpen() {
        ReleaseCounts draftOpen = new ReleaseCounts(1, 1, 0, 0, 0, 0);

        assertThat(NextStep.of(true, true, NO_CHANGES, draftOpen)).isEqualTo(NextStep.AWAIT_CHANGES);
    }

    @Test
    void collectingComesBeforeEveryStageOfAReleaseThatAlreadyExists() {
        ReleaseCounts everything = new ReleaseCounts(3, 1, 1, 1, 0, 2);

        assertThat(NextStep.of(true, true, SOME_CHANGES, everything)).isEqualTo(NextStep.COLLECT_CHANGES);
    }

    @Test
    void eachStageOfAReleaseComesInTurn() {
        assertThat(NextStep.of(true, true, SOME_CHANGES, new ReleaseCounts(3, 1, 1, 1, 0, 0)))
                .isEqualTo(NextStep.FINISH_REVIEW);
        assertThat(NextStep.of(true, true, SOME_CHANGES, new ReleaseCounts(2, 1, 0, 1, 0, 0)))
                .isEqualTo(NextStep.PUBLISH_RELEASE);
        assertThat(NextStep.of(true, true, SOME_CHANGES, new ReleaseCounts(1, 1, 0, 0, 0, 0)))
                .isEqualTo(NextStep.CONTINUE_DRAFT);
    }

    @Test
    void everythingDoneIsAStepOfItsOwn() {
        ReleaseCounts allPublished = new ReleaseCounts(2, 0, 0, 0, 2, 0);

        assertThat(NextStep.of(true, true, SOME_CHANGES, allPublished)).isEqualTo(NextStep.NOTHING_WAITING);
    }

    @Test
    void everyStepNamesThreeKeysAPageCanRender() {
        for (NextStep step : NextStep.values()) {
            assertThat(step.getLabelKey()).isEqualTo("ui.overview.next." + step.name());
            assertThat(step.getBodyKey()).endsWith(".body");
            assertThat(step.getActionKey()).endsWith(".action");
        }
    }
}
