package com.u1_2.effect.dashboard.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.dashboard.application.DashboardLayoutService;
import com.u1_2.effect.dashboard.application.DashboardService;
import com.u1_2.effect.dashboard.application.DashboardTrendService;
import com.u1_2.effect.dashboard.application.QualityDashboardService;
import com.u1_2.effect.dashboard.presentation.dto.CsDashboardResponse;
import com.u1_2.effect.dashboard.presentation.dto.DashboardLayoutResponse;
import com.u1_2.effect.dashboard.presentation.dto.DashboardSummaryResponse;
import com.u1_2.effect.dashboard.presentation.dto.DashboardTrendResponse;
import com.u1_2.effect.dashboard.presentation.dto.GetDashboardRequest;
import com.u1_2.effect.dashboard.presentation.dto.GetDashboardTrendRequest;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse;
import com.u1_2.effect.dashboard.presentation.dto.SaveDashboardLayoutRequest;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.global.dto.SuccessMessage;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;
    private final QualityDashboardService qualityService;
    private final DashboardLayoutService layoutService;
    private final DashboardTrendService trendService;

    @Operation(summary = "CS 대시보드의 접수·처리 중·종료·추정 손실 조회")
    @GetMapping("/dashboard/cs")
    public ResponseEntity<ResponseDto<CsDashboardResponse>> getCs(
            @Valid GetDashboardRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.getCs(request, user.role())));
    }

    @Operation(summary = "CS 대시보드의 주요 현황 요약")
    @GetMapping("/dashboard/cs/summary")
    public ResponseEntity<ResponseDto<DashboardSummaryResponse>> getCsSummary(
            @Valid GetDashboardRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.getCsSummary(request, user.role())));
    }

    @Operation(summary = "품질 대시보드의 KPI·불량 유형·조사 우선순위 조회")
    @GetMapping("/dashboard/quality")
    public ResponseEntity<ResponseDto<QualityDashboardResponse>> getQuality(
            @Valid GetDashboardRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(qualityService.get(request, user.role())));
    }

    @Operation(summary = "품질 대시보드의 주요 현황 요약")
    @GetMapping("/dashboard/quality/summary")
    public ResponseEntity<ResponseDto<DashboardSummaryResponse>> getQualitySummary(
            @Valid GetDashboardRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(qualityService.getSummary(request, user.role())));
    }

    @Operation(summary = "내 품질 대시보드의 저장 배치와 기본 배치 조회")
    @GetMapping("/dashboard/layout/quality")
    public ResponseEntity<ResponseDto<DashboardLayoutResponse>> getQualityLayout(@CurrentUser AuthUser user) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(layoutService.getQuality(user)));
    }

    @Operation(summary = "내 품질 대시보드의 위젯 순서·크기·숨김 저장")
    @PutMapping("/dashboard/layout/quality")
    public ResponseEntity<ResponseDto<Void>> saveQualityLayout(
            @Valid @RequestBody SaveDashboardLayoutRequest request, @CurrentUser AuthUser user
    ) {
        layoutService.saveQuality(request, user);
        return ResponseEntity.ok(ResponseDto.ofSuccess(SuccessMessage.UPDATE_SUCCESS));
    }

    @Operation(summary = "제품·불량 유형별 주간 접수 추이와 대책 시행일 조회")
    @GetMapping("/dashboard/trend")
    public ResponseEntity<ResponseDto<DashboardTrendResponse>> getTrend(
            @Valid GetDashboardTrendRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(trendService.get(request, user.role())));
    }
}
