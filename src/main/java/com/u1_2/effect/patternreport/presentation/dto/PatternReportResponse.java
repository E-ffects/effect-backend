package com.u1_2.effect.patternreport.presentation.dto;

import com.u1_2.effect.patternreport.domain.PatternReportBody;
import com.u1_2.effect.patternreport.domain.ReportDepartment;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PatternReportResponse(Header header, PatternReportBody report, List<Department> departments) {

    public record Header(long reportId, String patternId, String title, LocalDate from, LocalDate to, String product,
                         long generatedBy, OffsetDateTime generatedAt) {
    }

    public record Department(ReportDepartment code, String name, boolean recommended) {
    }

    public record History(List<Header> items, long total, int page, int size) {
    }
}
