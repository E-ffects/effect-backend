package com.u1_2.effect.global.time;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class KoreaTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KoreaTime() {
    }

    // DB에서 UTC로 읽어도 실제 시점은 유지하고 API의 표시 시간대만 서울로 맞춤
    public static OffsetDateTime inSeoul(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(ZONE).toOffsetDateTime();
    }

    // FastAPI가 비교하는 pcb 시각은 초 단위임. 정각의 초를 포함하고 소수점 이하는 버림
    public static String formatPcbTime(OffsetDateTime time) {
        return inSeoul(time).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
