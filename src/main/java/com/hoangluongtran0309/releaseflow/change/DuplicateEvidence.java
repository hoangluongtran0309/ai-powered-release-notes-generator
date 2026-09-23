package com.hoangluongtran0309.releaseflow.change;

/** The parts of a similarity score, each from 0 to 1 and rounded to three decimals. */
public record DuplicateEvidence(double title, double content, double paths) {
}
