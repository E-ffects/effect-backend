package com.u1_2.effect.patternreport.application;

import com.u1_2.effect.patternreport.domain.PatternReportBody;
import com.u1_2.effect.patternreport.domain.PatternReportBody.Assessment;
import com.u1_2.effect.patternreport.domain.PatternReportBody.EvidenceCase;
import com.u1_2.effect.patternreport.domain.PatternReportBody.EvidenceItem;
import com.u1_2.effect.patternreport.domain.PatternReportBody.Finding;
import com.u1_2.effect.patternreport.domain.ReportDepartment;
import com.u1_2.effect.patternreport.presentation.dto.CreatePatternDeliveryRequest;
import com.u1_2.effect.patternreport.presentation.dto.PatternDeliveryResponse;
import com.u1_2.effect.patternreport.presentation.dto.PatternReportResponse.Department;
import com.u1_2.effect.patternreport.presentation.dto.PatternReportResponse.Header;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class PatternReportDelivery {

    List<Department> departments(PatternReportBody report) {
        return Arrays.stream(ReportDepartment.values()).map(department -> department(report, department)).toList();
    }

    PatternDeliveryResponse preview(Header header, PatternReportBody report, CreatePatternDeliveryRequest request) {
        var selected = request.department();
        var actions = report.actions().stream().filter(action -> selected.getLabel().equals(action.department())).toList();
        var assessments = switch (selected) {
            case PROCESS_ENGINEERING, EQUIPMENT -> assessments(report, "OCCURRENCE");
            case QUALITY -> assessments(report, "DETECTION_ESCAPE");
            case PRODUCTION, PURCHASING -> List.<Assessment>of();
        };
        var findings = switch (selected) {
            case PROCESS_ENGINEERING -> findings(report, "OCCURRENCE");
            case QUALITY -> findings(report, "DETECTION_ESCAPE");
            case EQUIPMENT -> report.findings().stream().filter(f -> "equipment_event".equals(f.sourceType())).toList();
            case PRODUCTION, PURCHASING -> List.<Finding>of();
        };
        boolean quality = selected == ReportDepartment.QUALITY;
        boolean process = selected == ReportDepartment.PROCESS_ENGINEERING || selected == ReportDepartment.EQUIPMENT;
        var evidence = quality || process ? evidence(report, quality) : List.<EvidenceCase>of();
        // 전달본은 저장된 숫자·판정을 사용하며 요청 문구와 기한만 QA 입력으로 바꿀 수 있음
        String requestNote = StringUtils.hasText(request.requestNote()) ? request.requestNote().strip()
                : selected.getDefaultRequest();
        return new PatternDeliveryResponse(header, department(report, selected), report.narrative(), report.totals(), report.products(),
                requestNote, request.replyDueDate(), assessments, findings,
                quality || process ? report.processFlow() : List.of(),
                selected == ReportDepartment.PRODUCTION ? report.lots() : List.of(),
                process ? report.equipment() : List.of(),
                selected == ReportDepartment.PURCHASING ? report.materials() : List.of(), actions, evidence);
    }

    private Department department(PatternReportBody report, ReportDepartment department) {
        boolean recommended = report.actions().stream().anyMatch(action -> department.getLabel().equals(action.department()));
        return new Department(department, department.getLabel(), recommended);
    }

    private List<Assessment> assessments(PatternReportBody report, String kind) {
        return report.assessments().stream().filter(assessment -> kind.equals(assessment.kind())).toList();
    }

    private List<Finding> findings(PatternReportBody report, String kind) {
        return report.findings().stream().filter(finding -> kind.equals(finding.kind())).toList();
    }

    private List<EvidenceCase> evidence(PatternReportBody report, boolean quality) {
        // 품질 전달본에는 검사값, 공정·설비 전달본에는 공정 관측값을 남김
        return report.evidenceCases().stream().map(row -> new EvidenceCase(row.rmaId(), row.openedOn(), row.partNo(),
                row.detectionStage(), row.qaStatus(), row.membershipReason(), row.selectionReason(),
                row.items().stream().map(item -> new EvidenceItem(item.lotId(), item.symptom(), item.claimedQuantity(),
                        item.returnedQuantity(), item.verifiedQuantity(), quality ? item.measurements() : List.of(),
                        quality ? List.of() : item.observations())).toList())).toList();
    }
}
