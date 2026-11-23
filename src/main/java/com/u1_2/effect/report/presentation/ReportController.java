package com.u1_2.effect.report.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.report.application.ReportService;
import com.u1_2.effect.report.presentation.dto.EditReportRequest;
import com.u1_2.effect.report.presentation.dto.ReplaceReportRequest;
import com.u1_2.effect.report.presentation.dto.ReportDetailResponse;
import com.u1_2.effect.report.presentation.dto.ReportHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;

    @Operation(summary = "저장된 RMA 보고서와 검토 상태 목록 조회")
    @GetMapping
    public ResponseEntity<ResponseDto<ReportHistoryResponse>> history(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.history(page, size, user)));
    }

    @Operation(summary = "저장된 RMA 보고서 상세 조회")
    @GetMapping("/{rmaId}")
    public ResponseEntity<ResponseDto<ReportDetailResponse>> get(@PathVariable String rmaId, @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.get(rmaId, user)));
    }

    @Operation(summary = "저장된 AI 분석으로 보고서 최초 생성")
    @PostMapping("/{rmaId}")
    public ResponseEntity<ResponseDto<ReportDetailResponse>> create(@PathVariable String rmaId, @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.create(rmaId, user)));
    }

    @Operation(summary = "현재 분석으로 비교용 보고서 초안 만들기")
    @PostMapping("/{rmaId}/draft")
    public ResponseEntity<ResponseDto<ReportDetailResponse.Draft>> draft(
            @PathVariable String rmaId, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.draft(rmaId, user)));
    }

    @Operation(summary = "보고서 본문 수정 및 검토 상태 초기화")
    @PutMapping("/{rmaId}")
    public ResponseEntity<ResponseDto<ReportDetailResponse>> edit(
            @PathVariable String rmaId, @Valid @RequestBody EditReportRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.edit(rmaId, request, user)));
    }

    @Operation(summary = "현재 분석으로 만든 비교 초안을 저장 보고서로 교체")
    @PostMapping("/{rmaId}/replace")
    public ResponseEntity<ResponseDto<ReportDetailResponse>> replace(
            @PathVariable String rmaId, @Valid @RequestBody ReplaceReportRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.replace(rmaId, request.content(), user)));
    }

    @Operation(summary = "보고서 검토 확인")
    @PostMapping("/{rmaId}/review")
    public ResponseEntity<ResponseDto<ReportDetailResponse.Review>> review(
            @PathVariable String rmaId, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.review(rmaId, true, user)));
    }

    @Operation(summary = "보고서 검토 확인 취소")
    @DeleteMapping("/{rmaId}/review")
    public ResponseEntity<ResponseDto<ReportDetailResponse.Review>> cancelReview(
            @PathVariable String rmaId, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.review(rmaId, false, user)));
    }
}
