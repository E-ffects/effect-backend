package com.u1_2.effect.rma.application.review;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.rma.domain.RmaClaimRepository;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository;
import com.u1_2.effect.rma.domain.review.CauseDecisionRepository;
import com.u1_2.effect.rma.domain.review.CauseDecisionRepository.Decision;
import com.u1_2.effect.rma.domain.review.RmaInvestigationRepository;
import com.u1_2.effect.rma.domain.review.RmaInvestigationRepository.Assessment;
import com.u1_2.effect.rma.domain.review.RmaInvestigationRepository.NewCause;
import com.u1_2.effect.rma.presentation.dto.DecideRmaCauseRequest;
import com.u1_2.effect.rma.presentation.dto.DecideRmaCauseRequest.CandidateStatus;
import com.u1_2.effect.rma.presentation.dto.DecideRmaCauseRequest.Outcome;
import com.u1_2.effect.rma.presentation.dto.RmaCauseDecisionResponse;
import com.u1_2.effect.user.application.AuthService;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CauseDecisionService {

    private final RmaClaimRepository claimRepository;
    private final CauseDecisionRepository repository;
    private final RmaAnalysisRepository analysisRepository;
    private final RmaInvestigationRepository investigationRepository;
    private final AuthService authService;
    private final JsonMapper jsonMapper;
    private final Clock appClock;

    @Transactional
    public RmaCauseDecisionResponse decide(String rmaId, DecideRmaCauseRequest request, AuthUser user) {
        RoleAccess.require(user.role(), Role.QUALITY);
        String editor = authService.getCurrentUser(user.id()).getName();
        // QA 수정·분석 완료와 같은 접수 잠금을 써서 판정에 사용할 최신 등급과 결과를 고정함
        claimRepository.requireForUpdate(rmaId);
        var target = repository.findLatestCompleted(rmaId)
                .filter(row -> row.analysisId().equals(request.analysisId()))
                .orElseThrow(() -> new ApiException(RmaErrorCode.ANALYSIS_MISMATCH));
        if (target.approvalStatus() != null && !"WAITING".equals(target.approvalStatus())) {
            throw new ApiException(RmaErrorCode.CAUSE_ALREADY_DECIDED);
        }
        String level = analysisRepository.findLatestQaLevel(rmaId).orElse(target.analysisLevel());
        boolean confirmed = request.decision() != Outcome.REJECTED;
        validateOutcome(level, request.decision());
        JsonNode adopted = validateCandidates(request, target.result(), confirmed);

        OffsetDateTime now = OffsetDateTime.now(appClock);
        String status = request.decision().name();
        repository.save(new Decision(rmaId, target.analysisId(), status, level,
                jsonMapper.writeValueAsString(request.candidates()), request.comment(), editor, now, confirmed));

        // 반려는 판정 이력만 보관함. 승인·확인일 때만 원본 DB에 원인과 조사 연결을 함께 남김
        if (confirmed) {
            saveConfirmedCause(rmaId, adopted, level, editor, request.comment(), now);
            claimRepository.updateQaStatus(rmaId, "CAUSE_DECIDED");
        }
        return new RmaCauseDecisionResponse(rmaId, status, editor, now);
    }

    private void validateOutcome(String level, Outcome outcome) {
        boolean valid = switch (level == null ? "" : level) {
            case "L3" -> outcome == Outcome.APPROVED || outcome == Outcome.REJECTED;
            case "L1", "L2" -> outcome == Outcome.CONFIRMED;
            default -> false;
        };
        if (!valid) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private JsonNode validateCandidates(DecideRmaCauseRequest request, String result, boolean requiresAdoption) {
        Map<String, JsonNode> known = new HashMap<>();
        if (result != null) {
            for (JsonNode cause : jsonMapper.readTree(result).path("cause_candidates")) {
                known.put(cause.path("cause_code").asString(""), cause);
            }
        }
        var submitted = new HashSet<String>();
        JsonNode adopted = null;
        for (var candidate : request.candidates()) {
            if (!submitted.add(candidate.causeCode()) || !known.containsKey(candidate.causeCode())) {
                throw new ApiException(CommonErrorCode.INVALID_INPUT);
            }
            if (candidate.status() == CandidateStatus.ADOPTED) {
                if (adopted != null) {
                    throw new ApiException(CommonErrorCode.INVALID_INPUT);
                }
                adopted = known.get(candidate.causeCode());
            }
        }
        if (requiresAdoption && adopted == null) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
        return adopted;
    }

    private void saveConfirmedCause(String rmaId, JsonNode adopted, String level, String editor,
                                    String comment, OffsetDateTime now) {
        // 설비와 공정은 요청이 아닌 저장된 AI 후보에서 가져옴
        String code = adopted.path("cause_code").stringValue();
        String equipmentId = adopted.path("equipment_id").asString(null);
        String processId = adopted.path("process_id").asString(null);
        String timestamp = KoreaTime.formatPcbTime(now);

        investigationRepository.lockCauseMatching();
        var assessment = investigationRepository.findAssessment(code, equipmentId).orElseGet(() -> {
            String title = investigationRepository.findCauseName(code)
                    .orElseThrow(() -> new ApiException(CommonErrorCode.INVALID_INPUT));
            String id = String.format(Locale.ROOT, "INV-26-%04d", investigationRepository.nextInvestigationNumber());
            var created = new Assessment(id, "CAUSE-" + UUID.randomUUID());
            String scope = jsonMapper.createObjectNode().put("rma_id", rmaId).put("cause_code", code).toString();
            investigationRepository.insertInvestigation(new NewCause(id, created.causeAssessmentId(), code,
                    title, processId, equipmentId, level, scope, editor, timestamp));
            return created;
        });
        investigationRepository.linkClaim(rmaId, assessment, "IC-" + UUID.randomUUID(), "CC-" + UUID.randomUUID() + "-",
                comment == null ? "" : comment, timestamp);
    }
}
