package com.u1_2.effect.dashboard.application;

import com.u1_2.effect.dashboard.domain.QualityDashboardRepository;
import com.u1_2.effect.dashboard.domain.QualityDashboardRepository.Investigation;
import com.u1_2.effect.dashboard.domain.QualityDashboardRepository.Window;
import com.u1_2.effect.dashboard.presentation.dto.GetDashboardRequest;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Comparison;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Defect;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Kpis;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Period;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Priority;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Rate;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Recurrence;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Repeat;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.Repeats;
import com.u1_2.effect.dashboard.presentation.dto.QualityDashboardResponse.ScorePart;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
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
    private final Clock appClock;

    public QualityDashboardResponse get(GetDashboardRequest request, Role role) {
        RoleAccess.require(role, Role.QUALITY);
        var period = DashboardPeriod.resolve(request.from(), request.to(), LocalDate.now(appClock.withZone(KoreaTime.ZONE)));
        var window = new Window(period.previousStart(), period.start(), period.end());
        var totals = repository.findTotals(window);
        var defects = repository.findDefects(window).stream().map(d -> new Defect(d.defectType(), d.count())).toList();
        var investigations = repository.findInvestigations(window,
                period.to().minusDays(29).atStartOfDay(KoreaTime.ZONE).toOffsetDateTime());

        // 같은 조사에 기간 내 접수가 두 건 이상이면 반복 조사로 집계함
        var repeats = investigations.stream().filter(i -> i.count() >= 2)
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
                defects, repeats.stream().limit(5).toList(), priorities(investigations));
    }

    private List<Priority> priorities(List<Investigation> investigations) {
        double maxFrequency = investigations.stream().mapToDouble(this::frequency).max().orElse(0);
        long maxLoss = investigations.stream().mapToLong(Investigation::loss).max().orElse(0);
        return investigations.stream().map(i -> priority(i, maxFrequency, maxLoss))
                .sorted(Comparator.comparingDouble(Priority::score).reversed().thenComparing(Priority::investigationId))
                .limit(5).toList();
    }

    private Priority priority(Investigation row, double maxFrequency, long maxLoss) {
        var severity = Severity.from(row.severity());
        double trend = row.previousCount() == 0
                ? (row.count() >= 2 ? 100 : 0)
                : Math.clamp((row.count() - row.previousCount()) * 100.0 / row.previousCount(), 0, 100);
        // 원본 화면의 가중치를 유지하고 손실 분자는 조사 원장의 전체 합계를 사용함
        var parts = List.of(
                new ScorePart("count", "발생 건수 및 수량", 0.3, normalized(frequency(row), maxFrequency),
                        "%d건, 확인 불량 %d개".formatted(row.count(), row.defectQty())),
                new ScorePart("loss", "가정 단가 기반 추정 손실", 0.3, normalized(row.loss(), maxLoss),
                        "%d원".formatted(row.loss())),
                new ScorePart("severity", "심각도", 0.2, severity.score, "최고 " + severity.label),
                new ScorePart("trend", "증가 추세", 0.2, rounded(trend),
                        "직전 %d건 → %d건".formatted(row.previousCount(), row.count()))
        );
        double score = rounded(parts.stream().mapToDouble(p -> p.score() * p.weight()).sum());
        boolean highRisk = severity == Severity.CRITICAL || row.recurrence() || row.recentCount() >= 3;
        return new Priority(row.investigationId(), row.title(), row.analysisLevel(), row.product(), row.defectType(),
                row.count(), row.defectQty(), row.loss(), severity.label, row.previousCount(), highRisk, score,
                score >= 70 ? "즉시" : score >= 50 ? "금주내" : "금월내", parts, row.rmaId(), row.evidence());
    }

    private double frequency(Investigation row) {
        return row.count() + row.defectQty() / 10.0;
    }

    private double normalized(double value, double max) {
        return max <= 0 ? 0 : rounded(Math.clamp(value / max * 100, 0, 100));
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

    private enum Severity {
        CRITICAL(9, 100, "CRITICAL"), HIGH(7, 70, "HIGH"), MEDIUM(5, 40, "MEDIUM"),
        LOW(1, 10, "LOW"), UNDECIDED(0, 0, "판정 보류");

        private final int minimum;
        private final int score;
        private final String label;

        Severity(int minimum, int score, String label) {
            this.minimum = minimum;
            this.score = score;
            this.label = label;
        }

        // v5의 0~9 값을 품질 화면 배지와 점수에 쓰는 기존 구간으로 변환함
        static Severity from(int value) {
            for (var severity : values()) {
                if (value >= severity.minimum) {
                    return severity;
                }
            }
            return UNDECIDED;
        }
    }
}
