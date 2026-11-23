package com.u1_2.effect.dashboard.application;

import com.u1_2.effect.dashboard.domain.DashboardRepository;
import com.u1_2.effect.dashboard.presentation.dto.CsDashboardResponse;
import com.u1_2.effect.dashboard.presentation.dto.CsDashboardResponse.Kpis;
import com.u1_2.effect.dashboard.presentation.dto.CsDashboardResponse.Period;
import com.u1_2.effect.dashboard.presentation.dto.GetDashboardRequest;
import com.u1_2.effect.global.time.KoreaDateRange;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final DashboardRepository repository;
    private final Clock appClock;

    public CsDashboardResponse getCs(GetDashboardRequest request, Role role) {
        RoleAccess.require(role, Role.CS);

        var asOf = KoreaTime.inSeoul(OffsetDateTime.now(appClock));
        var period = KoreaDateRange.resolve(request.from(), request.to(), asOf.toLocalDate());
        var values = repository.findKpis(period.start(), period.end());
        return new CsDashboardResponse(new Period(period.from(), period.to()), asOf,
                new Kpis(values.receivedCount(), values.pendingCount(), values.closedCount(), values.estimatedLossKrw()));
    }
}
