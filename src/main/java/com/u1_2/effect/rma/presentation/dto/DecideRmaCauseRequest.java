package com.u1_2.effect.rma.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DecideRmaCauseRequest(
        @NotBlank String analysisId, @NotNull Outcome decision,
        @NotNull List<@NotNull @Valid Candidate> candidates, String comment
) {
    public enum Outcome {
        APPROVED, REJECTED, CONFIRMED
    }

    public enum CandidateStatus {
        ADOPTED, REJECTED
    }

    public record Candidate(@NotBlank String causeCode, @NotNull CandidateStatus status) {
    }
}
