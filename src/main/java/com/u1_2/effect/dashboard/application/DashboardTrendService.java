package com.u1_2.effect.dashboard.application;

import com.u1_2.effect.dashboard.domain.DashboardTrendRepository;
import com.u1_2.effect.dashboard.domain.DashboardTrendRepository.DailyCount;
import com.u1_2.effect.dashboard.domain.DashboardTrendRepository.Filter;
import com.u1_2.effect.dashboard.presentation.dto.DashboardTrendResponse;
import com.u1_2.effect.dashboard.presentation.dto.DashboardTrendResponse.Countermeasure;
import com.u1_2.effect.dashboard.presentation.dto.DashboardTrendResponse.Point;
import com.u1_2.effect.dashboard.presentation.dto.GetDashboardTrendRequest;
import com.u1_2.effect.global.time.KoreaDateRange;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.global.time.WeekRange;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Clock;
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
        var period = KoreaDateRange.resolve(request.from(), request.to(), KoreaTime.today(appClock));
        period.requireAtMostDays(367);
        var filter = new Filter(period.start(), period.end(), optional(request.product()), optional(request.defectType()));
        var points = weeklyPoints(period, repository.findDailyCounts(filter));

        // 품질 대책 시행 이력은 QUALITY 화면에만 표시함
        var markers = role == Role.QUALITY
                ? repository.findActionMarkers(filter).stream().map(m -> new Countermeasure(m.day(), m.label())).toList()
                : List.<Countermeasure>of();
        return new DashboardTrendResponse(points, markers);
    }

    private List<Point> weeklyPoints(KoreaDateRange period, List<DailyCount> dailyCounts) {
        var counts = dailyCounts.stream().collect(Collectors.toMap(DailyCount::day, DailyCount::count));
        return WeekRange.between(period.from(), period.to()).stream()
                .map(week -> new Point(week.from(), week.to(), week.label(),
                        week.dates().mapToLong(day -> counts.getOrDefault(day, 0L)).sum()))
                .toList();
    }

    private String optional(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
