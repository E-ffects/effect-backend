package com.u1_2.effect.meta.presentation;

import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.meta.application.MetaService;
import com.u1_2.effect.meta.presentation.dto.MetaResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 사용자가 접수에 쓸 선택지를 공통 응답 형식으로 조회함.
 */
@RestController
@RequiredArgsConstructor
public class MetaController {

    private final MetaService metaService;

    @Operation(summary = "RMA 기준 정보 조회")
    @GetMapping("/meta")
    public ResponseEntity<ResponseDto<MetaResponse>> getMeta() {
        return ResponseEntity.ok(ResponseDto.ofSuccess(metaService.getMeta()));
    }
}
