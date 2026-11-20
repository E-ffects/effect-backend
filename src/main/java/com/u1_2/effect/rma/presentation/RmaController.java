package com.u1_2.effect.rma.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.rma.application.RmaAnalysisService;
import com.u1_2.effect.rma.application.RmaDetailService;
import com.u1_2.effect.rma.presentation.dto.RmaAnalysisResponse;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 접수 내용과 분석 실행 결과를 서로 다른 조회 API로 제공함.
 */
@RestController
@RequiredArgsConstructor
public class RmaController {

    private final RmaDetailService rmaDetailService;
    private final RmaAnalysisService rmaAnalysisService;

    @Operation(summary = "RMA 상세 조회")
    @GetMapping("/rmas/{rmaId}")
    public ResponseEntity<ResponseDto<RmaDetailResponse>> getDetail(
            @PathVariable String rmaId, @CurrentUser AuthUser authUser
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(rmaDetailService.getDetail(rmaId, authUser.role())));
    }

    @Operation(summary = "RMA 최근 원인분석 상태 및 결과 조회")
    @GetMapping("/rmas/{rmaId}/analysis")
    public ResponseEntity<ResponseDto<RmaAnalysisResponse>> getAnalysis(
            @PathVariable String rmaId, @CurrentUser AuthUser authUser
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(rmaAnalysisService.getAnalysis(rmaId, authUser.role())));
    }
}
