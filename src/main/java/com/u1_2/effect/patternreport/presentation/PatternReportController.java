package com.u1_2.effect.patternreport.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.patternreport.application.PatternReportService;
import com.u1_2.effect.patternreport.presentation.dto.CreatePatternDeliveryRequest;
import com.u1_2.effect.patternreport.presentation.dto.CreatePatternReportRequest;
import com.u1_2.effect.patternreport.presentation.dto.PatternDeliveryResponse;
import com.u1_2.effect.patternreport.presentation.dto.PatternReportResponse;
import com.u1_2.effect.patternreport.presentation.dto.PatternReportResponse.History;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PatternReportController {

    private final PatternReportService service;

    @Operation(summary = "불량 패턴의 종합 보고서 생성")
    @PostMapping("/patterns/{patternId}/reports")
    public ResponseEntity<ResponseDto<PatternReportResponse>> create(
            @PathVariable @Size(max = 100) String patternId,
            @Valid @RequestBody CreatePatternReportRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.create(patternId, request, user)));
    }

    @Operation(summary = "저장한 불량 패턴 보고서 이력 조회")
    @GetMapping("/pattern-reports")
    public ResponseEntity<ResponseDto<History>> history(
            @RequestParam(name = "pattern_id", required = false) @Size(max = 100) String patternId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.history(patternId, page, size, user.role())));
    }

    @Operation(summary = "저장한 불량 패턴 보고서 상세 조회")
    @GetMapping("/pattern-reports/{reportId}")
    public ResponseEntity<ResponseDto<PatternReportResponse>> get(
            @PathVariable @Positive long reportId, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.get(reportId, user.role())));
    }

    @Operation(summary = "담당 부서별 불량 패턴 보고서 전달본 미리보기")
    @PostMapping("/pattern-reports/{reportId}/delivery-preview")
    public ResponseEntity<ResponseDto<PatternDeliveryResponse>> preview(
            @PathVariable @Positive long reportId, @Valid @RequestBody CreatePatternDeliveryRequest request,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.preview(reportId, request, user.role())));
    }
}
