package com.u1_2.effect.global.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class KoreaTimeTest {

    @Test
    void pcb_시각은_서울_기준으로_정각의_초까지_표시하고_소수점은_버린다() {
        var runtimeTime = OffsetDateTime.parse("2026-11-21T01:56:00.083880Z");
        assertThat(KoreaTime.formatPcbTime(runtimeTime)).isEqualTo("2026-11-21T10:56:00+09:00");
    }
}
