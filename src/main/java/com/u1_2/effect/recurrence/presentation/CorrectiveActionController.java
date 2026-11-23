package com.u1_2.effect.recurrence.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.recurrence.application.CorrectiveActionService;
import com.u1_2.effect.recurrence.presentation.dto.CorrectiveActionDetailResponse;
import com.u1_2.effect.recurrence.presentation.dto.ImplementCorrectiveActionRequest;
import com.u1_2.effect.recurrence.presentation.dto.SaveCorrectiveActionRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recurrence")
@RequiredArgsConstructor
public class CorrectiveActionController {

    private final CorrectiveActionService service;

    @Operation(summary = "조사에 대책 계획 등록")
    @PostMapping("/investigations/{investigationId}/actions")
    public ResponseEntity<ResponseDto<CorrectiveActionDetailResponse>> create(
            @PathVariable String investigationId,
            @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 128) String key,
            @Valid @RequestBody SaveCorrectiveActionRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.create(investigationId, key, request, user)));
    }

    @Operation(summary = "개별 대책의 계획과 현재 상태 조회")
    @GetMapping("/actions/{actionId}")
    public ResponseEntity<ResponseDto<CorrectiveActionDetailResponse>> get(
            @PathVariable String actionId, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.get(actionId, user)));
    }

    @Operation(summary = "시행 전 대책 계획 변경")
    @PutMapping("/actions/{actionId}")
    public ResponseEntity<ResponseDto<CorrectiveActionDetailResponse>> update(
            @PathVariable String actionId, @Valid @RequestBody SaveCorrectiveActionRequest request,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.update(actionId, request, user)));
    }

    @Operation(summary = "대책 시행일을 기록하고 효과 검증 대기로 전환")
    @PostMapping("/actions/{actionId}/implement")
    public ResponseEntity<ResponseDto<CorrectiveActionDetailResponse>> implement(
            @PathVariable String actionId, @Valid @RequestBody ImplementCorrectiveActionRequest request,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.implement(actionId, request.implementedAt(), user)));
    }
}
