package com.u1_2.effect.dashboard.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.dashboard.application.DashboardService;
import com.u1_2.effect.dashboard.presentation.dto.CsDashboardResponse;
import com.u1_2.effect.dashboard.presentation.dto.GetDashboardRequest;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @Operation(summary = "CS 대시보드의 접수·처리 중·종료·추정 손실 조회")
    @GetMapping("/dashboard/cs")
    public ResponseEntity<ResponseDto<CsDashboardResponse>> getCs(
            @Valid GetDashboardRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.getCs(request, user.role())));
    }
}
