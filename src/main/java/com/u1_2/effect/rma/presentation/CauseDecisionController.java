package com.u1_2.effect.rma.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.rma.application.review.CauseDecisionService;
import com.u1_2.effect.rma.presentation.dto.DecideRmaCauseRequest;
import com.u1_2.effect.rma.presentation.dto.RmaCauseDecisionResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "test", "prod"})
@RequiredArgsConstructor
public class CauseDecisionController {

    private final CauseDecisionService service;

    @Operation(summary = "최신 분석의 원인 후보 판정 저장")
    @PostMapping("/rmas/{rmaId}/cause-decision")
    public ResponseEntity<ResponseDto<RmaCauseDecisionResponse>> decide(
            @PathVariable String rmaId, @Valid @RequestBody DecideRmaCauseRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.decide(rmaId, request, user)));
    }
}
