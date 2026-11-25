package com.u1_2.effect.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄 작업을 켬. 분석 폴링·사례 갱신·알림 갱신이 서로 밀리지 않도록 스레드 수는 spring.task.scheduling.pool.size로 지정함.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
