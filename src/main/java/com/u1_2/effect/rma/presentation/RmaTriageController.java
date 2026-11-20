package com.u1_2.effect.rma.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.rma.application.RmaTriageService;
import com.u1_2.effect.rma.presentation.dto.RmaTriageUpdateResponse;
import com.u1_2.effect.rma.presentation.dto.UpdateRmaTriageRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 품질관리자가 임시 QA 판정을 확인하거나 기존 판정을 수정할 때 사용함.
 */
@RestController
@Profile({"local", "test"})
@ConditionalOnProperty(name = "ai.mode", havingValue = "mock", matchIfMissing = true)
@RequiredArgsConstructor
public class RmaTriageController {

    private final RmaTriageService service;

    @Operation(summary = "품질관리자의 QA 판정 확인·수정")
    @PutMapping("/rmas/{rmaId}/triage")
    public ResponseEntity<ResponseDto<RmaTriageUpdateResponse>> update(
            @PathVariable String rmaId, @Valid @RequestBody UpdateRmaTriageRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.update(rmaId, request, user)));
    }
}
