package com.u1_2.effect.dashboard.application;

import com.u1_2.effect.dashboard.domain.QualityDashboardRepository;
import com.u1_2.effect.dashboard.presentation.dto.GetDashboardRequest;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Comparison;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Defect;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Kpis;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Period;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Priority;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.ScorePart;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Rate;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Recurrence;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Repeat;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Repeats;
import com.u1_2.effect.global.time.KoreaDateRange;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.investigation.application.InvestigationRankingService;
import com.u1_2.effect.investigation.application.InvestigationRankingService.Ranked;
import com.u1_2.effect.investigation.domain.InvestigationRankingRepository.Investigation;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Clock;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// KPI와 순위가 진행 중인 갱신을 사이에 두고 서로 다른 시점의 DB를 읽지 않도록 함
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class QualityDashboardService {

    private final QualityDashboardRepository repository;
    private final InvestigationRankingService ranking;
    private final Clock appClock;

    public QualityDashboardResponse get(GetDashboardRequest request, Role role) {
        RoleAccess.require(role, Role.QUALITY);
        var period = KoreaDateRange.resolve(request.from(), request.to(), KoreaTime.today(appClock));
        var totals = repository.findTotals(period);
        var defects = repository.findDefects(period).stream().map(d -> new Defect(d.defectType(), d.count())).toList();
        var ranked = ranking.rank(period, null);

        // 같은 조사에 기간 내 접수가 두 건 이상이면 반복 조사로 집계함
        var repeats = ranked.stream().map(Ranked::investigation).filter(i -> i.count() >= 2)
                .sorted(Comparator.comparingLong(Investigation::count).reversed()
                        .thenComparing(Investigation::investigationId))
                .map(i -> new Repeat(i.investigationId(), i.title(), i.product(), i.defectType(), i.causeName(),
                        i.count(), i.previousCount(), i.rmaId()))
                .toList();
        var kpis = new Kpis(
                comparison(totals.received(), totals.previousReceived()),
                new Repeats(repeats.size(), repeats.stream().filter(i -> i.prevCount() < 2).count()),
                new Recurrence(totals.recurrenceSuspected()),
                comparison(totals.loss(), totals.previousLoss()),
                rate(totals.confirmed(), totals.received()),
                rate(totals.effectiveActions(), totals.completedActions())
        );
        return new QualityDashboardResponse(new Period(period.from(), period.to()), kpis,
                defects, repeats.stream().limit(5).toList(),
                ranked.stream().limit(5).map(this::toPriority).toList());
    }

    private Priority toPriority(Ranked ranked) {
        var row = ranked.investigation();
        var breakdown = ranked.breakdown().stream()
                .map(p -> new ScorePart(p.key(), p.label(), p.weight(), p.score(), p.detail())).toList();
        return new Priority(row.investigationId(), row.title(), row.analysisLevel(), row.product(), row.defectType(),
                row.count(), row.defectQty(), row.loss(), ranked.severity(), row.previousCount(), ranked.highRisk(),
                ranked.score(), ranked.urgency(), breakdown, row.rmaId(), row.evidence());
    }

    private Comparison comparison(long value, long previous) {
        return new Comparison(value, previous == 0 ? null : rounded((value - (double) previous) * 100 / previous));
    }

    private Rate rate(long numerator, long denominator) {
        return new Rate(denominator == 0 ? null : rounded(numerator * 100.0 / denominator), numerator, denominator);
    }

    private double rounded(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
