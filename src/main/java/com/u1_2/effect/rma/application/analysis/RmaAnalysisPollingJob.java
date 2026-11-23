package com.u1_2.effect.rma.application.analysis;

import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 화면이 꺼져 있어도 조회 예정 시각이 지난 분석 작업을 찾아 처리함.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@Profile({"local", "test", "prod"})
@ConditionalOnProperty(name = "app.analysis.polling.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RmaAnalysisPollingJob {

    private final RmaAnalysisRepository repository;
    private final RmaAnalysisPollingService pollingService;

    @Scheduled(fixedDelay = 250)
    public void pollDueAnalyses() {
        // 화면 요청과 별개로 DB에 저장된 조회 예정 시각이 지난 작업을 처리함
        for (String id : repository.findDueAnalyses()) {
            try {
                pollingService.poll(id);
            } catch (RuntimeException e) {
                log.error("분석 작업 상태를 저장하지 못했습니다. analysisId={}", id, e);
            }
        }
    }
}
