package com.u1_2.effect.rma.presentation.communication;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.rma.application.communication.RmaCommunicationService;
import com.u1_2.effect.rma.presentation.communication.dto.CreateRmaCommunicationRequest;
import com.u1_2.effect.rma.presentation.communication.dto.CreateRmaServiceActionRequest;
import com.u1_2.effect.rma.presentation.communication.dto.RmaCommunicationResponse;
import com.u1_2.effect.rma.presentation.communication.dto.RmaCommunicationResponse.History;
import com.u1_2.effect.rma.presentation.communication.dto.RmaServiceActionResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rmas/{rmaId}")
@RequiredArgsConstructor
public class RmaCommunicationController {

    private final RmaCommunicationService service;

    @Operation(summary = "고객 회신 기록 저장")
    @PostMapping("/communications")
    public ResponseEntity<ResponseDto<RmaCommunicationResponse>> recordMessage(
            @PathVariable String rmaId, @Valid @RequestBody CreateRmaCommunicationRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.recordMessage(rmaId, request, user)));
    }

    @Operation(summary = "RMA 회신 이력 조회")
    @GetMapping("/communications")
    public ResponseEntity<ResponseDto<History>> findHistory(@PathVariable String rmaId, @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.findHistory(rmaId, user.role())));
    }

    @Operation(summary = "고객 처리 예약 또는 실행 결과 저장")
    @PostMapping("/service-action")
    public ResponseEntity<ResponseDto<RmaServiceActionResponse>> recordAction(
            @PathVariable String rmaId, @Valid @RequestBody CreateRmaServiceActionRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.recordAction(rmaId, request, user.role())));
    }
}
