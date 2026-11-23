package com.u1_2.effect.rma.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.rma.application.RmaDetailService;
import com.u1_2.effect.rma.application.RmaListService;
import com.u1_2.effect.rma.application.analysis.RmaAnalysisService;
import com.u1_2.effect.rma.application.analysis.RmaSimilarService;
import com.u1_2.effect.rma.presentation.dto.GetRmaListRequest;
import com.u1_2.effect.rma.presentation.dto.RmaAnalysisResponse;
import com.u1_2.effect.rma.presentation.dto.RmaDetailResponse;
import com.u1_2.effect.rma.presentation.dto.RmaListResponse;
import com.u1_2.effect.rma.presentation.dto.RmaSimilarResponse;
import com.u1_2.effect.rma.presentation.dto.SearchRmaRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RmaController {

    private final RmaDetailService rmaDetailService;
    private final RmaAnalysisService rmaAnalysisService;
    private final RmaListService rmaListService;
    private final RmaSimilarService rmaSimilarService;

    @Operation(summary = "RMA 목록 검색")
    @GetMapping("/rmas")
    public ResponseEntity<ResponseDto<RmaListResponse>> getList(
            @Valid SearchRmaRequest request, @Valid GetRmaListRequest pagination, @CurrentUser AuthUser authUser
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(rmaListService.getList(request, pagination, authUser.role())));
    }

    @Operation(summary = "검색 조건에 맞는 전체 RMA 목록 CSV 다운로드")
    @GetMapping("/rmas/export")
    public ResponseEntity<byte[]> exportList(@Valid SearchRmaRequest request, @CurrentUser AuthUser authUser) {
        var rows = rmaListService.getExportRows(request, authUser.role());
        var disposition = ContentDisposition.attachment().filename("rma.csv").build();
        // 기존 첨부 다운로드와 같이 파일 본문을 그대로 내려줌
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(RmaCsvWriter.write(rows, authUser.role()));
    }

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

    @Operation(summary = "RMA의 과거 유사 사례 조회")
    @GetMapping("/rmas/{rmaId}/similar")
    public ResponseEntity<ResponseDto<RmaSimilarResponse>> getSimilar(
            @PathVariable String rmaId, @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit,
            @CurrentUser AuthUser authUser
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(rmaSimilarService.getSimilar(rmaId, limit, authUser.role())));
    }
}
