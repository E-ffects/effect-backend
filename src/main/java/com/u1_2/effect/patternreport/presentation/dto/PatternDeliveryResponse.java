package com.u1_2.effect.patternreport.presentation.dto;

import com.u1_2.effect.patternreport.domain.PatternReportBody.Action;
import com.u1_2.effect.patternreport.domain.PatternReportBody.Assessment;
import com.u1_2.effect.patternreport.domain.PatternReportBody.EquipmentCount;
import com.u1_2.effect.patternreport.domain.PatternReportBody.EvidenceCase;
import com.u1_2.effect.patternreport.domain.PatternReportBody.Finding;
import com.u1_2.effect.patternreport.domain.PatternReportBody.LotCount;
import com.u1_2.effect.patternreport.domain.PatternReportBody.Material;
import com.u1_2.effect.patternreport.domain.PatternReportBody.Narrative;
import com.u1_2.effect.patternreport.domain.PatternReportBody.ProcessStep;
import com.u1_2.effect.patternreport.domain.PatternReportBody.ProductCount;
import com.u1_2.effect.patternreport.domain.PatternReportBody.Totals;
import java.time.LocalDate;
import java.util.List;

public record PatternDeliveryResponse(
        PatternReportResponse.Header header, PatternReportResponse.Department department,
        Narrative narrative, Totals totals, List<ProductCount> products, String requestNote, LocalDate replyDueDate,
        List<Assessment> assessments, List<Finding> findings, List<ProcessStep> processFlow,
        List<LotCount> lots, List<EquipmentCount> equipment, List<Material> materials,
        List<Action> actions, List<EvidenceCase> evidenceCases
) {
}
