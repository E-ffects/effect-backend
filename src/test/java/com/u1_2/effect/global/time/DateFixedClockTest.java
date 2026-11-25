package com.u1_2.effect.global.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DateFixedClockTest {

    private static final LocalDate DATA_DATE = LocalDate.of(2026, 12, 31);

    @Test
    void 날짜는_기준일로_고정하고_시각은_실제_서울_시각을_쓴다() {
        // 실제 시각: 2026-11-25 14:23:05 (서울)
        Clock real = Clock.fixed(Instant.parse("2026-11-25T05:23:05Z"), ZoneOffset.UTC);

        Clock clock = new DateFixedClock(DATA_DATE, real);

        assertThat(LocalDateTime.now(clock.withZone(KoreaTime.ZONE)))
                .isEqualTo(LocalDateTime.of(2026, 12, 31, 14, 23, 5));
    }

    @Test
    void 실제_시각이_흐르면_같은_만큼_흐른다() {
        Instant start = Instant.parse("2026-11-25T01:00:00Z");
        Clock clock1 = new DateFixedClock(DATA_DATE, Clock.fixed(start, ZoneId.of("UTC")));
        Clock clock2 = new DateFixedClock(DATA_DATE, Clock.fixed(start.plusSeconds(90), ZoneId.of("UTC")));

        assertThat(Duration.between(clock1.instant(), clock2.instant())).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void 서울_자정이_지나도_날짜는_기준일이다() {
        // 서울 2026-11-26 00:30 = UTC 2026-11-25 15:30
        Clock real = Clock.fixed(Instant.parse("2026-11-25T15:30:00Z"), ZoneOffset.UTC);

        assertThat(KoreaTime.today(new DateFixedClock(DATA_DATE, real))).isEqualTo(DATA_DATE);
    }
}
