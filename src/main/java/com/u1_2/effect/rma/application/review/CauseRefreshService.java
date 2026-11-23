package com.u1_2.effect.rma.application.review;

import com.u1_2.effect.config.ai.AiClient;
import com.u1_2.effect.config.ai.AiClient.CaseRefreshRequest;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.rma.domain.review.CauseDecisionRepository;
import com.u1_2.effect.rma.domain.review.CauseDecisionRepository.Refresh;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Profile({"local", "test", "prod"})
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CauseRefreshService {

    private static final int MAX_ATTEMPTS = 6;

    private final CauseDecisionRepository repository;
    private final AiClient aiClient;

    @Transactional
    public void refresh(String analysisId) {
        repository.lockDueRefresh(analysisId).ifPresent(this::refresh);
    }

    private void refresh(Refresh row) {
        int attempts = row.attempts() + 1;
        boolean indexed;
        try {
            indexed = aiClient.refreshCase(new CaseRefreshRequest(row.rmaId(), row.analysisId(),
                    row.decision(), KoreaTime.inSeoul(row.decidedAt())));
        } catch (ApiException e) {
            indexed = false;
        }
        // 첫 호출이 실패하면 1분 간격으로 최대 5회 더 시도함
        String status = indexed ? "DONE" : attempts < MAX_ATTEMPTS ? "PENDING" : "FAILED";
        repository.updateRefresh(row.analysisId(), status, attempts);
        if ("FAILED".equals(status)) {
            log.warn("AI 사례 갱신 재시도 종료. analysisId={}, attempts={}", row.analysisId(), attempts);
        }
    }
}
