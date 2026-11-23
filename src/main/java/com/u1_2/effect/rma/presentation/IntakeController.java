package com.u1_2.effect.rma.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.rma.application.intake.IntakeRegistrationService.Registration;
import com.u1_2.effect.rma.application.intake.IntakeWorkflowService;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.presentation.dto.CreateIntakeRequest;
import com.u1_2.effect.rma.presentation.dto.RmaIntakeResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@Profile({"local", "test", "prod"})
@RequiredArgsConstructor
public class IntakeController {

    private final IntakeWorkflowService service;

    @Operation(summary = "RMA 신규 접수")
    @PostMapping(value = "/intake", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseDto<RmaIntakeResponse>> create(
            @Valid @ModelAttribute CreateIntakeRequest request,
            @CurrentUser AuthUser user,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            MultipartHttpServletRequest files
    ) {
        // 폼에서 단건으로 읽혔어도 실제로 보낸 원문 파일이 한 개인지 확인함
        if (files.getFiles("raw_file").size() != 1) {
            throw new ApiException(RmaErrorCode.UNSUPPORTED_FILE);
        }
        var registration = new Registration(request.customerId(), request.reportedPartNo(), request.occurredAt(),
                request.detectionStage(), request.claimedDefectQty(), request.inputQty(), request.reportedLotNo(),
                request.reportedDateCode(), request.panelId(), request.customerRequest(), request.rawFile(),
                files.getFiles("attachments[]"));
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.create(registration, user, key)));
    }

    @Operation(summary = "실패한 RMA 접수를 AI 분류부터 재처리")
    @PostMapping("/rmas/{rmaId}/intake/retry")
    public ResponseEntity<ResponseDto<RmaIntakeResponse>> retry(@PathVariable String rmaId, @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.retry(rmaId, user.role())));
    }
}
