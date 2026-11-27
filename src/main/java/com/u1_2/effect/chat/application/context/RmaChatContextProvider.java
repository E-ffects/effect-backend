package com.u1_2.effect.chat.application.context;

import com.u1_2.effect.chat.domain.ChatScope;
import com.u1_2.effect.chat.domain.ChatScopeType;
import com.u1_2.effect.rma.application.RmaDetailService;
import com.u1_2.effect.rma.application.analysis.RmaAnalysisService;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 단건 RMA: 접수 상세와 최신 분석 결과. CS는 두 서비스가 이미 적용하는 CS 필터 결과를 받음 */
@Component
@RequiredArgsConstructor
public class RmaChatContextProvider implements ChatContextProvider {

    private static final List<String> UNUSED_RMA_FIELDS = List.of("follow_up_questions", "triage_proposal", "report",
            "follow_up_draft_available", "error");

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
        context.set("rma", rma(jsonMapper.valueToTree(detail)));
        if (analysis.result() == null) {
            // 분석이 없으면 AI가 NEED_ANALYSIS로 안내함
            context.putNull("analysis");
        } else {
            // 진행 상태·실행 기록(trace)·분석 경고는 AI가 답변에 쓰지 않아 보내지 않음
            var summary = context.putObject("analysis");
            summary.put("analysis_id", analysis.analysisId());
            summary.put("analysis_state", analysis.analysisState());
            summary.set("as_of", jsonMapper.valueToTree(analysis.asOf()));
            summary.set("result", analysis.result());
            summary.set("cause_decision", jsonMapper.valueToTree(analysis.causeDecision()));
        }
        return context;
    }

    /** 화면 응답에서 AI가 답변에 쓰지 않는 필드를 뺌. 토큰을 줄이려는 것이고 권한 필터는 조회 서비스에서 이미 끝남 */
    private ObjectNode rma(ObjectNode detail) {
        detail.remove(UNUSED_RMA_FIELDS);
        if (detail.path("triage") instanceof ObjectNode triage) {
            triage.retain("analysis_level");
        }
        return detail;
    }
}
