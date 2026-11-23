package com.u1_2.effect.pattern.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.pattern.application.PatternService;
import com.u1_2.effect.pattern.presentation.dto.GetPatternDetailRequest;
import com.u1_2.effect.pattern.presentation.dto.GetPatternListRequest;
import com.u1_2.effect.pattern.presentation.dto.PatternDetailResponse;
import com.u1_2.effect.pattern.presentation.dto.PatternListResponse;
import com.u1_2.effect.pattern.presentation.dto.PatternReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PatternController {

    private final PatternService service;

    @Operation(summary = "반복 불량 조사 순위와 기간 내 주요 원인 집계 조회")
    @GetMapping("/patterns")
    public ResponseEntity<ResponseDto<PatternListResponse>> list(
            @Valid GetPatternListRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.list(request, user.role())));
    }

    @Operation(summary = "불량 조사에 연결된 근거 RMA와 제조 이력·대책 조회")
    @GetMapping("/patterns/{patternId}")
    public ResponseEntity<ResponseDto<PatternDetailResponse>> detail(
            @PathVariable String patternId, @Valid GetPatternDetailRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.detail(patternId, request, user.role())));
    }

    @Operation(summary = "조사 기록 기반 불량 패턴 요약 보고서 조회")
    @GetMapping("/dashboard/pattern-report/{patternId}")
    public ResponseEntity<ResponseDto<PatternReportResponse>> report(
            @PathVariable String patternId, @Valid GetPatternDetailRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.report(patternId, request, user.role())));
    }
}
