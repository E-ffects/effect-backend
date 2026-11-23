package com.u1_2.effect.global.config;

import com.u1_2.effect.global.time.KoreaTime;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 업무 시간은 서울 기준으로 사용함. 로컬 시연에서는 명세의 고정 시각을 적용할 수 있음.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock appClock(@Value("${app.clock.fixed:}") String fixedTime) {
        if (fixedTime.isBlank()) {
            return Clock.system(KoreaTime.ZONE);
        }
        return Clock.fixed(OffsetDateTime.parse(fixedTime).toInstant(), KoreaTime.ZONE);
    }
}
