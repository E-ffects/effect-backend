package com.u1_2.effect.global.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 날짜는 데이터 기준일로 고정하고 시각만 실제로 흐르는 시계.
 * 재시작해도 시각이 되돌아가지 않고, 같은 날 만든 기록에는 순서와 처리 시간이 남음.
 */
public final class DateFixedClock extends Clock {

    private final LocalDate date;
    private final Clock system;

    public DateFixedClock(LocalDate date, Clock system) {
        this.date = date;
        this.system = system;
    }

    @Override
    public ZoneId getZone() {
        return system.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new DateFixedClock(date, system.withZone(zone));
    }

    @Override
    public Instant instant() {
        // 기준일의 날짜에 서울 기준 실제 시각을 붙임
        Clock seoul = system.withZone(KoreaTime.ZONE);
        return date.atTime(LocalTime.now(seoul)).atZone(KoreaTime.ZONE).toInstant();
    }
}
