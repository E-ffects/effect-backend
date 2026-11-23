package com.u1_2.effect.ai.presentation;

import com.u1_2.effect.config.ai.AiClient;
import com.u1_2.effect.config.ai.AiClient.Health;
import com.u1_2.effect.global.dto.ResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AiHealthController {

    private final ObjectProvider<AiClient> aiClient;

    @Operation(summary = "AI 서비스 상태 조회")
    @GetMapping("/ai/health")
    public ResponseEntity<ResponseDto<Health>> getHealth() {
        // 상태 확인 요청은 장애 정보도 응답 본문에 담아 화면 배너를 갱신할 수 있게 함
        AiClient client = aiClient.getIfAvailable();
        Health health = client == null
                ? new Health(false, null, "unconfigured", "unknown", "unknown")
                : client.health();
        return ResponseEntity.ok(ResponseDto.ofSuccess(health));
    }
}
