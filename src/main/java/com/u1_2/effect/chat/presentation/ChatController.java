package com.u1_2.effect.chat.presentation;

import com.u1_2.effect.chat.application.ChatService;
import com.u1_2.effect.chat.presentation.dto.ChatRequest;
import com.u1_2.effect.chat.presentation.dto.ChatResponse;
import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 범위(RMA·패턴)와 역할(QUALITY·CS)은 서비스 계층이 나눔. 진입점은 하나 */
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "에이전트 패널 질문. thread_id 없이 보내면 그 범위의 새 대화를 시작함")
    @PostMapping("/chat")
    public ResponseEntity<ResponseDto<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest request, @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(chatService.chat(request, user)));
    }
}
