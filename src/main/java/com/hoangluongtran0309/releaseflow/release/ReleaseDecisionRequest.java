package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.change.ChangeReviewRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A decision on one change of a release in review. Both actions carry the category and
 * breaking flag the reviewer saw, so a stale page never approves a classification it did
 * not show.
 */
public class ReleaseDecisionRequest {

    @NotNull(message = "Action must be approve or edit.")
    private ReviewAction action;

    @NotBlank(message = "Category is required.")
    private String category;

    @NotNull(message = "Breaking must be true or false.")
    private Boolean breaking;

    @Size(max = 2000, message = "Note must not exceed 2000 characters.")
    private String note;

    public ReviewAction getAction() {
        return action;
    }

    public void setAction(ReviewAction action) {
        this.action = action;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category == null ? null : category.strip();
    }

    public Boolean getBreaking() {
        return breaking;
    }

    public void setBreaking(Boolean breaking) {
        this.breaking = breaking;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note == null || note.isBlank() ? null : note.strip();
    }

    ChangeReviewRequest changeReview() {
        ChangeReviewRequest review = new ChangeReviewRequest();
        review.setCategory(category);
        review.setBreaking(breaking);
        return review;
    }
}
