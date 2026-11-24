package com.u1_2.effect.rma.application.intake;

import com.u1_2.effect.rma.presentation.intake.dto.IntakeErrorResponse;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

public record IntakeFeedback(List<String> questions, IntakeErrorResponse error) {

    public static IntakeFeedback fromStored(JsonMapper mapper, String questionsJson, String errorCode, String errorDetail) {
        // 저장된 질문의 SQL NULL과 JSON null을 모두 질문 없음으로 읽음
        String[] questions = questionsJson == null ? null : mapper.readValue(questionsJson, String[].class);
        return new IntakeFeedback(questions == null ? null : List.of(questions),
                errorCode == null ? null : new IntakeErrorResponse(errorCode, errorDetail));
    }
}
