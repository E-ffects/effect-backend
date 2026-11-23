package com.u1_2.effect.investigation.application;

import com.u1_2.effect.global.time.KoreaDateRange;
import com.u1_2.effect.investigation.domain.InvestigationRankingRepository;
import com.u1_2.effect.investigation.domain.InvestigationRankingRepository.Investigation;
import com.u1_2.effect.rma.domain.RmaSeverity;
import java.util.Comparator;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestigationRankingService {

    private final InvestigationRankingRepository repository;

    // 대시보드와 패턴이 같은 조회 기준과 우선순위 산식을 사용함
    public List<Ranked> rank(KoreaDateRange period, String product) {
        var investigations = repository.find(period, product);
        double maxFrequency = investigations.stream().mapToDouble(InvestigationRankingService::frequency).max().orElse(0);
        long maxLoss = investigations.stream().mapToLong(Investigation::loss).max().orElse(0);
        return investigations.stream().map(i -> priority(i, maxFrequency, maxLoss))
                .sorted(Comparator.comparingDouble(Ranked::score).reversed()
                        .thenComparing(row -> row.investigation().investigationId()))
                .toList();
    }

    private static Ranked priority(Investigation row, double maxFrequency, long maxLoss) {
        var severity = RmaSeverity.from(row.severity());
        double trend = row.previousCount() == 0
                ? (row.count() >= 2 ? 100 : 0)
                : Math.clamp((row.count() - row.previousCount()) * 100.0 / row.previousCount(), 0, 100);
        // 원본 화면의 가중치를 유지하고 손실 분자는 조사 원장의 전체 합계를 사용함
        var parts = List.of(
                new ScorePart("count", "발생 건수 및 수량", 0.3, normalized(frequency(row), maxFrequency),
                        "%d건, 확인 불량 %d개".formatted(row.count(), row.defectQty())),
                new ScorePart("loss", "가정 단가 기반 추정 손실", 0.3, normalized(row.loss(), maxLoss),
                        "%d원".formatted(row.loss())),
                new ScorePart("severity", "심각도", 0.2, severity.priorityScore(), "최고 " + severity.label()),
                new ScorePart("trend", "증가 추세", 0.2, rounded(trend),
                        "직전 %d건 → %d건".formatted(row.previousCount(), row.count()))
        );
        double score = rounded(parts.stream().mapToDouble(p -> p.score() * p.weight()).sum());
        boolean highRisk = severity == RmaSeverity.CRITICAL || row.recurrence() || row.recentCount() >= 3;
        return new Ranked(row, score, severity.label(), highRisk,
                score >= 70 ? "즉시" : score >= 50 ? "금주내" : "금월내", parts);
    }

    public record Ranked(
            Investigation investigation, double score, String severity, boolean highRisk, String urgency,
            List<ScorePart> breakdown
    ) {
    }

    public record ScorePart(String key, String label, double weight, double score, String detail) {
    }

    private static double frequency(Investigation row) {
        return row.count() + row.defectQty() / 10.0;
    }

    private static double normalized(double value, double max) {
        return max <= 0 ? 0 : rounded(Math.clamp(value / max * 100, 0, 100));
    }

    private static double rounded(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
