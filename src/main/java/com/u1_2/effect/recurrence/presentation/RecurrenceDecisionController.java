package com.u1_2.effect.recurrence.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.recurrence.application.RecurrenceDecisionService;
import com.u1_2.effect.recurrence.presentation.dto.DecideRecurrenceRequest;
import com.u1_2.effect.recurrence.presentation.dto.RecurrenceDecisionResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RecurrenceDecisionController {

    private final RecurrenceDecisionService service;

    @Operation(summary = "품질 담당자가 RMA 재발 여부를 최초 확정")
    @PostMapping("/rmas/{rmaId}/recurrence")
    public ResponseEntity<ResponseDto<RecurrenceDecisionResponse>> decide(
            @PathVariable String rmaId, @Valid @RequestBody DecideRecurrenceRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.decide(rmaId, request.decision(), user)));
    }
}
