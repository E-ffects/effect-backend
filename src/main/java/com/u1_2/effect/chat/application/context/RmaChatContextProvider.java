package com.u1_2.effect.chat.application.context;

import com.u1_2.effect.chat.domain.ChatScope;
import com.u1_2.effect.chat.domain.ChatScopeType;
import com.u1_2.effect.rma.application.RmaDetailService;
import com.u1_2.effect.rma.application.analysis.RmaAnalysisService;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 단건 RMA: 접수 상세와 최신 분석 결과. CS는 두 서비스가 이미 적용하는 CS 필터 결과를 받음 */
@Component
@RequiredArgsConstructor
public class RmaChatContextProvider implements ChatContextProvider {

    private final RmaDetailService detailService;
    private final RmaAnalysisService analysisService;
    private final JsonMapper jsonMapper;

    @Override
    public ChatScopeType type() {
        return ChatScopeType.RMA;
    }

    @Override
    public JsonNode load(ChatScope scope, Role role) {
        RoleAccess.requireMember(role);
        var detail = detailService.getDetail(scope.id(), role);
        var analysis = analysisService.getAnalysis(scope.id(), role);

        var context = jsonMapper.createObjectNode();
        context.set("rma", jsonMapper.valueToTree(detail));
        if (analysis.result() == null) {
            // 분석이 없으면 AI가 NEED_ANALYSIS로 안내함
            context.putNull("analysis");
        } else {
            // 진행 상태·실행 기록(trace)은 답변 근거가 아니라 보내지 않음
            var summary = context.putObject("analysis");
            summary.put("analysis_id", analysis.analysisId());
            summary.put("analysis_state", analysis.analysisState());
            summary.set("as_of", jsonMapper.valueToTree(analysis.asOf()));
            summary.set("result", analysis.result());
            summary.set("warnings", analysis.warnings());
            summary.set("cause_decision", jsonMapper.valueToTree(analysis.causeDecision()));
        }
        return context;
    }
}
