package com.u1_2.effect.global.time;

import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

public record KoreaDateRange(LocalDate from, LocalDate to) {

    public static KoreaDateRange resolve(LocalDate requestedFrom, LocalDate requestedTo, LocalDate today) {
        return resolve(requestedFrom, requestedTo, today, 30);
    }

    public static KoreaDateRange resolve(
            LocalDate requestedFrom, LocalDate requestedTo, LocalDate today, int defaultDays
    ) {
        LocalDate to = requestedTo == null ? today : requestedTo;
        validateYear(to);
        LocalDate from = requestedFrom == null ? to.minusDays(defaultDays - 1L) : requestedFrom;
        validateYear(from);
        if (from.isAfter(to)) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
        return new KoreaDateRange(from, to);
    }

    public OffsetDateTime start() {
        return KoreaTime.startOfDay(from);
    }

    // 시작일과 종료일을 모두 포함하도록 서울 기준 다음 날 자정을 제외 경계로 씀
    public OffsetDateTime end() {
        return KoreaTime.startOfDay(to.plusDays(1));
    }

    public OffsetDateTime previousStart() {
        return KoreaTime.startOfDay(from.minusDays(days()));
    }

    public void requireAtMostDays(int maxDays) {
        if (days() > maxDays) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    private static void validateYear(LocalDate date) {
        if (date.getYear() < 1 || date.getYear() > 9999) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
