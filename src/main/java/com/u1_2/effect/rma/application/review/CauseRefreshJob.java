package com.u1_2.effect.rma.application.review;

import com.u1_2.effect.rma.domain.review.CauseDecisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration(proxyBeanMethods = false)
@Profile({"local", "test", "prod"})
@ConditionalOnProperty(name = "app.case-refresh.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class CauseRefreshJob {

    private final CauseDecisionRepository repository;
    private final CauseRefreshService service;

    @Scheduled(fixedDelay = 1000)
    public void refreshDueCases() {
        // DB에 커밋된 예약만 읽음. 사례 갱신 장애가 품질관리자의 판정 저장을 되돌리지 않음
        for (String analysisId : repository.findDueRefreshes()) {
            try {
                service.refresh(analysisId);
            } catch (RuntimeException e) {
                log.error("AI 사례 갱신 상태 저장 실패. analysisId={}", analysisId, e);
            }
        }
    }
}
