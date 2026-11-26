package com.u1_2.effect.patternreport.application;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.pattern.domain.PatternErrorCode;
import com.u1_2.effect.pattern.domain.PatternRepository;
import com.u1_2.effect.pattern.domain.PatternRepository.Selection;
import com.u1_2.effect.patternreport.domain.PatternReportBody;
import com.u1_2.effect.patternreport.domain.PatternReportBody.Action;
import com.u1_2.effect.patternreport.domain.PatternReportBody.Assessment;
import com.u1_2.effect.patternreport.domain.PatternReportBody.EquipmentCount;
import com.u1_2.effect.patternreport.domain.PatternReportBody.EvidenceCase;
import com.u1_2.effect.patternreport.domain.PatternReportBody.EvidenceItem;
import com.u1_2.effect.patternreport.domain.PatternReportBody.LotCount;
import com.u1_2.effect.patternreport.domain.PatternReportBody.MonthCount;
import com.u1_2.effect.patternreport.domain.PatternReportBody.Narrative;
import com.u1_2.effect.patternreport.domain.PatternReportBody.ProcessStep;
import com.u1_2.effect.patternreport.domain.PatternReportBody.Totals;
import com.u1_2.effect.patternreport.domain.PatternReportErrorCode;
import com.u1_2.effect.patternreport.domain.PatternReportSourceRepository;
import com.u1_2.effect.patternreport.domain.PatternReportSourceRepository.ActionCheck;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class PatternReportDraftFactory {

    private static final int MAX_EVIDENCE_CASES = 3;
    private static final String OCCURRENCE = "OCCURRENCE";
    private static final String DETECTION_ESCAPE = "DETECTION_ESCAPE";

    private final PatternRepository patterns;
    private final PatternReportSourceRepository source;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Draft build(Selection selection) {
        // 보고서의 여러 집계를 같은 DB 스냅샷에서 읽어 발생 건수와 근거가 어긋나지 않게 함
        var profile = patterns.findProfile(selection)
                .orElseThrow(() -> new ApiException(PatternErrorCode.PATTERN_NOT_FOUND));
        var counts = patterns.findCauses(selection);
        long cases = counts.stream().mapToLong(PatternRepository.CauseCount::count).sum();
        if (cases == 0) {
            throw new ApiException(PatternReportErrorCode.PATTERN_REPORT_NO_CASES);
        }
        long unresolved = counts.stream().filter(cause -> cause.cause() == null)
                .mapToLong(PatternRepository.CauseCount::count).sum();
        var quantities = source.quantities(selection);
        var lots = patterns.findLots(selection).stream().map(lot -> new LotCount(lot.lot(), lot.count())).toList();
        var equipment = patterns.findEquipment(selection).stream()
                .map(row -> new EquipmentCount(row.equipmentId(), row.count())).toList();
        var assessments = source.assessments(selection);
        var totals = new Totals(cases, lots.size(), quantities.claimed(), quantities.verified(), profile.loss(), unresolved);
        var months = source.months(selection);
        return new Draft(profile.title(), new PatternReportBody(profile.analysisLevel(), profile.status(),
                narrative(assessments, months, totals), totals, months, source.products(selection), lots, equipment,
                assessments, source.findings(selection), flow(assessments), source.materials(selection),
                actions(selection), evidenceCases(selection)));
    }

    private Narrative narrative(List<Assessment> assessments, List<MonthCount> months, Totals totals) {
        // 확정 판정의 명칭과 저장된 발생 건수만으로 본문을 조립함
        String occurrence = confirmedNames(assessments, OCCURRENCE);
        String escape = confirmedNames(assessments, DETECTION_ESCAPE);
        String occurrenceText = occurrence.isEmpty() ? "확정된 발생 원인이 없다."
                : "조사에서 발생 원인으로 ‘%s’ 항목을 확인했다.".formatted(occurrence);
        String escapeText = escape.isEmpty() ? "확정된 검출 누락 원인이 없다."
                : "조사에서 검출 누락 원인으로 ‘%s’ 항목을 확인했다.".formatted(escape);
        String trend = "선택한 범위의 연결 접수는 %d건이다.".formatted(totals.claims());
        if (months.size() > 1) {
            var peak = months.stream().max(Comparator.comparingLong(MonthCount::claims)).orElseThrow();
            trend += " 최다 접수 월은 %s이며, %d건으로 전체의 약 %d%%를 차지했다."
                    .formatted(peak.month(), peak.claims(), Math.round(100.0 * peak.claims() / totals.claims()));
        }
        return new Narrative(occurrenceText + " " + escapeText, trend);
    }

    private String confirmedNames(List<Assessment> assessments, String kind) {
        return assessments.stream().filter(row -> kind.equals(row.kind()) && "CONFIRMED".equals(row.status()))
                .map(Assessment::name).distinct().collect(Collectors.joining(", "));
    }

    private List<ProcessStep> flow(List<Assessment> assessments) {
        var occurrence = confirmedProcesses(assessments, OCCURRENCE);
        var detection = confirmedProcesses(assessments, DETECTION_ESCAPE);
        return source.processes().stream().map(step -> new ProcessStep(step.processId(), step.name(), step.sequence(),
                step.inspection(), occurrence.contains(step.processId()), detection.contains(step.processId()))).toList();
    }

    private Set<String> confirmedProcesses(List<Assessment> assessments, String kind) {
        return assessments.stream().filter(a -> kind.equals(a.kind()) && "CONFIRMED".equals(a.status()))
                .map(Assessment::processId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private List<Action> actions(Selection selection) {
        var checks = source.checks(selection).stream().collect(groupingBy(ActionCheck::actionId,
                mapping(ActionCheck::check, Collectors.toList())));
        return source.actions(selection).stream().map(row -> new Action(row.actionId(), row.department(),
                row.description(), KoreaTime.inSeoul(row.dueAt()), KoreaTime.inSeoul(row.implementedAt()),
                row.verificationStatus(), checks.getOrDefault(row.actionId(), List.of()))).toList();
    }

    private List<EvidenceCase> evidenceCases(Selection selection) {
        return source.evidenceCases(selection, MAX_EVIDENCE_CASES).stream().map(row -> {
            var items = source.items(selection, row.rmaId()).stream().map(item -> new EvidenceItem(item.lotId(),
                    item.symptom(), item.claimedQuantity(), item.returnedQuantity(), item.verifiedQuantity(),
                    source.measurements(selection, item.rmaItemId()), source.observations(selection, item.rmaItemId())))
                    .toList();
            return new EvidenceCase(row.rmaId(), row.openedOn(), row.partNo(), row.detectionStage(), row.qaStatus(),
                    row.membershipReason(), row.confirmedTest() ? "CONFIRMED_TEST" : "LINKED_CASE", items);
        }).toList();
    }

    record Draft(String title, PatternReportBody body) {
    }
}
