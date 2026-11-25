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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Profile({"local", "test", "prod"})
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CauseRefreshService {

    private static final int MAX_ATTEMPTS = 6;
    /** AI 응답 대기(최대 5초)보다 길게 잡아 호출 도중 다른 실행기가 같은 갱신을 집지 않게 함 */
    private static final int LEASE_SECONDS = 30;

    private final CauseDecisionRepository repository;
    private final AiClient aiClient;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refresh(String analysisId) {
        // 선점을 먼저 커밋하고 트랜잭션 밖에서 AI를 호출해 DB 커넥션을 쥐고 기다리지 않음
        repository.claimDueRefresh(analysisId, LEASE_SECONDS).ifPresent(this::refresh);
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
        if (!repository.updateRefresh(row.analysisId(), status, attempts, row.claimedUntil())) {
            log.info("호출 중 판정이 다시 저장돼 이전 갱신 결과를 버립니다. analysisId={}", row.analysisId());
            return;
        }
        if ("FAILED".equals(status)) {
            log.warn("AI 사례 갱신 재시도 종료. analysisId={}, attempts={}", row.analysisId(), attempts);
        }
    }
}
