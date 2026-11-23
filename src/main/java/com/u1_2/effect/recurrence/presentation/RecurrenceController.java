package com.u1_2.effect.recurrence.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.recurrence.application.RecurrenceService;
import com.u1_2.effect.recurrence.presentation.dto.CorrectiveActionTrendResponse;
import com.u1_2.effect.recurrence.presentation.dto.CorrectiveActionsResponse;
import com.u1_2.effect.recurrence.presentation.dto.GetCorrectiveActionsRequest;
import com.u1_2.effect.recurrence.presentation.dto.GetRecurrenceReviewsRequest;
import com.u1_2.effect.recurrence.presentation.dto.RecurrenceReviewsResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recurrence")
@RequiredArgsConstructor
public class RecurrenceController {

    private final RecurrenceService service;

    @Operation(summary = "조사별 대책 목록과 확정 재발 접수 수 조회")
    @GetMapping("/actions")
    public ResponseEntity<ResponseDto<CorrectiveActionsResponse>> actions(
            @Valid GetCorrectiveActionsRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.actions(request, user.role())));
    }

    @Operation(summary = "대책 시행 전후 주간 접수 추이와 지표별 효과 검증 조회")
    @GetMapping("/actions/{actionId}/trend")
    public ResponseEntity<ResponseDto<CorrectiveActionTrendResponse>> trend(
            @PathVariable String actionId, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.trend(actionId, user.role())));
    }

    @Operation(summary = "대책·판정 상태별 재발 검토 이력 조회")
    @GetMapping("/reviews")
    public ResponseEntity<ResponseDto<RecurrenceReviewsResponse>> reviews(
            @Valid GetRecurrenceReviewsRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.reviews(request, user.role())));
    }
}
