package com.u1_2.effect.chat.application.context;

import com.u1_2.effect.chat.domain.ChatScope;
import com.u1_2.effect.chat.domain.ChatScopeType;
import com.u1_2.effect.pattern.application.PatternService;
import com.u1_2.effect.pattern.presentation.dto.GetPatternDetailRequest;
import com.u1_2.effect.user.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 불량 패턴(반복 불량 조사): 화면과 같은 기간의 상세 집계와 요약. PatternService가 QUALITY만 허용함 */
@Component
@RequiredArgsConstructor
public class PatternChatContextProvider implements ChatContextProvider {

    private final PatternService patternService;
    private final JsonMapper jsonMapper;

    @Override
    public ChatScopeType type() {
        return ChatScopeType.PATTERN;
    }

    @Override
    public JsonNode load(ChatScope scope, Role role) {
        var period = new GetPatternDetailRequest(scope.from(), scope.to(), null);
        var detail = patternService.detail(scope.id(), period, role);
        var report = patternService.report(scope.id(), period, role);

        var context = jsonMapper.createObjectNode();
        // 기간을 비우면 화면과 같이 기준일까지 최근 90일로 집계됨. 집계 기간은 report.status 문장에도 들어 있음
        context.putObject("period").put("from", scope.from() == null ? null : scope.from().toString())
                .put("to", scope.to() == null ? null : scope.to().toString());
        context.set("pattern", jsonMapper.valueToTree(detail));
        context.set("report", jsonMapper.valueToTree(report));
        return context;
    }
}
