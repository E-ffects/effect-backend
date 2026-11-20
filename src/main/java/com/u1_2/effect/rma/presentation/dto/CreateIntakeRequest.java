package com.u1_2.effect.rma.presentation.dto;

import com.u1_2.effect.rma.domain.DetectionStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * multipart에서 폼 값과 원문 파일을 받음. 폼 필드명은 API 계약의 snake_case를 사용함.
 */
public record CreateIntakeRequest(
        @BindParam("customer_id") @NotBlank String customerId,
        @BindParam("reported_part_no") @NotBlank String reportedPartNo,
        @BindParam("occurred_at") @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredAt,
        @BindParam("detection_stage") @NotNull DetectionStage detectionStage,
        @BindParam("claimed_defect_qty") @NotNull @Positive Long claimedDefectQty,
        @BindParam("input_qty") @NotNull @Positive Long inputQty,
        @BindParam("raw_file") @NotNull MultipartFile rawFile,
        @BindParam("reported_lot_no") String reportedLotNo,
        @BindParam("reported_date_code") String reportedDateCode,
        @BindParam("panel_id") String panelId,
        @BindParam("customer_request") String customerRequest
) {
}
