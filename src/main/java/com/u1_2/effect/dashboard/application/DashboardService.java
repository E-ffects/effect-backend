package com.u1_2.effect.dashboard.application;

import com.u1_2.effect.dashboard.domain.DashboardRepository;
import com.u1_2.effect.dashboard.presentation.dto.CsDashboardResponse;
import com.u1_2.effect.dashboard.presentation.dto.CsDashboardResponse.Kpis;
import com.u1_2.effect.dashboard.presentation.dto.CsDashboardResponse.Period;
import com.u1_2.effect.dashboard.presentation.dto.GetDashboardRequest;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Clock;
import java.time.LocalDate;
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
        LocalDate to = request.to() == null ? asOf.toLocalDate() : request.to();
        validateYear(to);
        LocalDate from = request.from() == null ? to.minusDays(29) : request.from();
        validateYear(from);
        if (from.isAfter(to)) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }

        // 종료일 전체가 포함되도록 서울 기준 다음 날 자정을 제외 경계로 넘김
        var values = repository.findKpis(from.atStartOfDay(KoreaTime.ZONE).toOffsetDateTime(),
                to.plusDays(1).atStartOfDay(KoreaTime.ZONE).toOffsetDateTime());
        return new CsDashboardResponse(new Period(from, to), asOf,
                new Kpis(values.receivedCount(), values.pendingCount(), values.closedCount(), values.estimatedLossKrw()));
    }

    private void validateYear(LocalDate date) {
        if (date.getYear() < 1 || date.getYear() > 9999) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
