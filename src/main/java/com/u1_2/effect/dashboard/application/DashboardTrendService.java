package com.u1_2.effect.dashboard.application;

import com.u1_2.effect.dashboard.domain.DashboardTrendRepository;
import com.u1_2.effect.dashboard.domain.DashboardTrendRepository.DailyCount;
import com.u1_2.effect.dashboard.domain.DashboardTrendRepository.Filter;
import com.u1_2.effect.dashboard.presentation.dto.DashboardTrendResponse;
import com.u1_2.effect.dashboard.presentation.dto.DashboardTrendResponse.Countermeasure;
import com.u1_2.effect.dashboard.presentation.dto.DashboardTrendResponse.Point;
import com.u1_2.effect.dashboard.presentation.dto.GetDashboardTrendRequest;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DashboardTrendService {

    private final DashboardTrendRepository repository;
    private final Clock appClock;

    public DashboardTrendResponse get(GetDashboardTrendRequest request, Role role) {
        RoleAccess.requireMember(role);
        var period = DashboardPeriod.resolve(request.from(), request.to(), LocalDate.now(appClock.withZone(KoreaTime.ZONE)));
        if (ChronoUnit.DAYS.between(period.from(), period.to()) > 366) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
        var filter = new Filter(period.start(), period.end(), optional(request.product()), optional(request.defectType()));
        var points = weeklyPoints(period, repository.findDailyCounts(filter));

        // 품질 대책 시행 이력은 QUALITY 화면에만 표시함
        var markers = role == Role.QUALITY
                ? repository.findActionMarkers(filter).stream().map(m -> new Countermeasure(m.day(), m.label())).toList()
                : List.<Countermeasure>of();
        return new DashboardTrendResponse(points, markers);
    }

    private List<Point> weeklyPoints(DashboardPeriod period, List<DailyCount> dailyCounts) {
        var counts = dailyCounts.stream().collect(Collectors.toMap(DailyCount::day, DailyCount::count));
        var points = new ArrayList<Point>();

        // 선택 시작일부터 7일씩 묶고 접수가 없는 주도 0으로 남겨 차트 간격을 유지함
        for (var from = period.from(); !from.isAfter(period.to()); from = from.plusDays(7)) {
            var lastDay = from.plusDays(6);
            var to = lastDay.isAfter(period.to()) ? period.to() : lastDay;
            long count = from.datesUntil(to.plusDays(1)).mapToLong(day -> counts.getOrDefault(day, 0L)).sum();
            points.add(new Point(from, to, from.getMonthValue() + "/" + from.getDayOfMonth(), count));
        }
        return points;
    }

    private String optional(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
