package com.u1_2.effect.alert.presentation;

import com.u1_2.effect.alert.application.AlertService;
import com.u1_2.effect.alert.domain.AlertRepository.Change;
import com.u1_2.effect.alert.presentation.dto.AlertIdsRequest;
import com.u1_2.effect.alert.presentation.dto.AlertsResponse;
import com.u1_2.effect.alert.presentation.dto.GetAlertsRequest;
import com.u1_2.effect.alert.presentation.dto.StarAlertRequest;
import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.global.dto.SuccessMessage;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService service;

    @Operation(summary = "내 품질 알림 목록과 미확인 건수 조회")
    @GetMapping
    public ResponseEntity<ResponseDto<AlertsResponse>> list(@Valid GetAlertsRequest request, @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.list(request, user)));
    }

    @Operation(summary = "내 알림 한 건을 읽음 처리")
    @PostMapping("/{id}/read")
    public ResponseEntity<ResponseDto<AlertsResponse.Item>> read(@PathVariable @Positive long id, @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.set(id, Change.READ, user)));
    }

    @Operation(summary = "내 알림 즐겨찾기 변경")
    @PostMapping("/{id}/star")
    public ResponseEntity<ResponseDto<AlertsResponse.Item>> star(
            @PathVariable @Positive long id, @Valid @RequestBody StarAlertRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.set(id, request.starred() ? Change.STAR : Change.UNSTAR, user)));
    }

    @Operation(summary = "내 알림 한 건 숨김")
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<Void>> delete(@PathVariable @Positive long id, @CurrentUser AuthUser user) {
        service.delete(id, user);
        return ResponseEntity.ok(ResponseDto.ofSuccess(SuccessMessage.DELETE_SUCCESS));
    }

    @Operation(summary = "내 선택 알림 읽음 처리")
    @PostMapping("/bulk-read")
    public ResponseEntity<ResponseDto<AlertsResponse.Updated>> bulkRead(
            @Valid @RequestBody AlertIdsRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(new AlertsResponse.Updated(service.bulk(request.ids(), Change.READ, user))));
    }

    @Operation(summary = "내 선택 알림 숨김")
    @PostMapping("/bulk-delete")
    public ResponseEntity<ResponseDto<AlertsResponse.Deleted>> bulkDelete(
            @Valid @RequestBody AlertIdsRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(new AlertsResponse.Deleted(service.bulk(request.ids(), Change.DELETE, user))));
    }

    @Operation(summary = "내 미확인 알림을 모두 읽음 처리")
    @PostMapping("/read-all")
    public ResponseEntity<ResponseDto<Void>> readAll(@CurrentUser AuthUser user) {
        service.readAll(user);
        return ResponseEntity.ok(ResponseDto.ofSuccess(SuccessMessage.UPDATE_SUCCESS));
    }
}
