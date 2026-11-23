package com.u1_2.effect.global.time;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public record WeekRange(LocalDate from, LocalDate to) {

    public static List<WeekRange> between(LocalDate from, LocalDate to) {
        var weeks = new ArrayList<WeekRange>();
        // 지정한 시작일부터 7일씩 묶고 마지막 주는 종료일에서 끊음
        for (var day = from; !day.isAfter(to); day = day.plusDays(7)) {
            var weekEnd = day.plusDays(6);
            weeks.add(new WeekRange(day, weekEnd.isAfter(to) ? to : weekEnd));
        }
        return weeks;
    }

    public Stream<LocalDate> dates() {
        return from.datesUntil(to.plusDays(1));
    }

    public String label() {
        return from.getMonthValue() + "/" + from.getDayOfMonth();
    }
}
