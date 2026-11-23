package com.u1_2.effect.alert.application;

import com.u1_2.effect.alert.domain.AlertSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.alert.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class AlertRefreshJob {

    private final AlertSourceRepository repository;

    @Scheduled(initialDelay = 1000, fixedDelay = 30000)
    @Transactional
    public void refresh() {
        repository.refresh();
    }
}
