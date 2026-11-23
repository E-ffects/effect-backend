package com.u1_2.effect.dashboard.application;

import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.global.time.KoreaTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

record DashboardPeriod(LocalDate from, LocalDate to) {

    static DashboardPeriod resolve(LocalDate requestedFrom, LocalDate requestedTo, LocalDate today) {
        LocalDate to = requestedTo == null ? today : requestedTo;
        validateYear(to);
        LocalDate from = requestedFrom == null ? to.minusDays(29) : requestedFrom;
        validateYear(from);
        if (from.isAfter(to)) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
        return new DashboardPeriod(from, to);
    }

    OffsetDateTime start() {
        return from.atStartOfDay(KoreaTime.ZONE).toOffsetDateTime();
    }

    // 시작일과 종료일을 모두 포함하도록 서울 기준 다음 날 자정을 제외 경계로 씀
    OffsetDateTime end() {
        return to.plusDays(1).atStartOfDay(KoreaTime.ZONE).toOffsetDateTime();
    }

    OffsetDateTime previousStart() {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return from.minusDays(days).atStartOfDay(KoreaTime.ZONE).toOffsetDateTime();
    }

    private static void validateYear(LocalDate date) {
        if (date.getYear() < 1 || date.getYear() > 9999) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
