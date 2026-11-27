package com.u1_2.effect.chat.presentation;

import com.u1_2.effect.chat.application.ChatService;
import com.u1_2.effect.chat.domain.ChatScopeType;
import com.u1_2.effect.chat.presentation.dto.ChatRequest;
import com.u1_2.effect.chat.presentation.dto.ChatResponse;
import com.u1_2.effect.chat.presentation.dto.ChatThreadResponse;
import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 범위(RMA·패턴·그 외)와 역할(QUALITY·CS)은 서비스 계층이 나눔. 진입점은 하나 */
@Validated
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

    @Operation(summary = "범위의 열린 대화 조회. 없으면 content가 null. GENERAL은 scope_id 없이 조회")
    @GetMapping("/chat/threads")
    public ResponseEntity<ResponseDto<ChatThreadResponse>> openThread(
            @RequestParam("scope_type") ChatScopeType scopeType,
            @RequestParam(value = "scope_id", required = false) @Size(max = 64) String scopeId,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(chatService.openThread(scopeType, scopeId, user).orElse(null)));
    }
}
