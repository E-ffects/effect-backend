package com.u1_2.effect.global.config;

import com.u1_2.effect.global.time.DateFixedClock;
import com.u1_2.effect.global.time.KoreaTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 업무 시간은 서울 기준으로 사용함.
 * - app.clock.fixed: 시각까지 고정 (테스트)
 * - app.clock.date: 날짜만 데이터 기준일로 고정하고 시각은 실제로 흐름 (운영 시연)
 * - 둘 다 없으면 실제 시계
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock appClock(@Value("${app.clock.fixed:}") String fixedTime,
                          @Value("${app.clock.date:}") String date) {
        if (!fixedTime.isBlank()) {
            return Clock.fixed(OffsetDateTime.parse(fixedTime).toInstant(), KoreaTime.ZONE);
        }
        if (!date.isBlank()) {
            return new DateFixedClock(LocalDate.parse(date), Clock.system(KoreaTime.ZONE));
        }
        return Clock.system(KoreaTime.ZONE);
    }
}
