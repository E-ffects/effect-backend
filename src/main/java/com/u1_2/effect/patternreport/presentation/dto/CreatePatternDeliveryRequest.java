package com.u1_2.effect.patternreport.presentation.dto;

import com.u1_2.effect.patternreport.domain.ReportDepartment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreatePatternDeliveryRequest(
        @NotNull ReportDepartment department, @Size(max = 2000) String requestNote, LocalDate replyDueDate
) {
}
