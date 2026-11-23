package com.u1_2.effect.rma.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.rma.application.analysis.RmaAnalysisRegistrationService;
import com.u1_2.effect.rma.presentation.dto.CreateRmaAnalysisRequest;
import com.u1_2.effect.rma.presentation.dto.RmaAnalysisRegistrationResponse;
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
public class RmaReanalysisController {

    private final RmaAnalysisRegistrationService service;

    @Operation(summary = "원인 재분석 등록 또는 실행 중인 작업 조회")
    @PostMapping("/rmas/{rmaId}/analysis")
    public ResponseEntity<ResponseDto<RmaAnalysisRegistrationResponse>> register(
            @PathVariable String rmaId, @CurrentUser AuthUser user,
            @Valid @RequestBody(required = false) CreateRmaAnalysisRequest request
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.register(rmaId, user.role(), request)));
    }
}
