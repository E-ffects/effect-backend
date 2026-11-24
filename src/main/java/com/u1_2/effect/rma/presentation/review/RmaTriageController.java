package com.u1_2.effect.rma.presentation.review;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.rma.application.review.RmaTriageService;
import com.u1_2.effect.rma.presentation.review.dto.RmaTriageUpdateResponse;
import com.u1_2.effect.rma.presentation.review.dto.UpdateRmaTriageRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "test", "prod"})
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
