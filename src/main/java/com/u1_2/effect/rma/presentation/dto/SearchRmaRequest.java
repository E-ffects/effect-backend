package com.u1_2.effect.rma.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record SearchRmaRequest(
        @Size(max = 200) String q,
        @Size(max = 100) String product,
        @Size(max = 100) String defectType,
        @Size(max = 50) String classification,
        @Size(max = 50) String status,
        @Min(0) @Max(9) Integer severity,
        @Size(max = 50) String recurrence,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @Min(0) Integer overDays,
        @Size(max = 300) String sort
) {
}
