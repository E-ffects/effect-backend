package com.u1_2.effect.rma.application.review;

import com.u1_2.effect.global.dto.PageParameters;
import com.u1_2.effect.global.dto.PageResponse;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.rma.domain.review.RmaReviewRepository;
import com.u1_2.effect.rma.domain.review.RmaReviewRepository.ApprovalLevel;
import com.u1_2.effect.rma.domain.review.RmaReviewRepository.ApprovalStatus;
import com.u1_2.effect.rma.presentation.review.dto.RmaApprovalResponse;
import com.u1_2.effect.rma.presentation.review.dto.RmaTriageReviewResponse;
import com.u1_2.effect.rma.presentation.review.dto.RmaTriageReviewResponse.Proposal;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 품질관리자 전용 목록임. 목록과 전체 건수를 같은 DB 시점 기준으로 읽음.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RmaReviewService {

    private final RmaReviewRepository repository;
    private final JsonMapper jsonMapper;

    public PageResponse<RmaApprovalResponse> getApprovals(Role role, ApprovalStatus status, ApprovalLevel level,
                                                          Integer page, Integer size) {
        RoleAccess.require(role, Role.QUALITY);
        var pagination = PageParameters.resolve(page, size);
        var levels = level.levels();
        var items = repository.findApprovals(status, levels, pagination.offset(), pagination.size()).stream()
                .map(row -> new RmaApprovalResponse(row.rmaId(), row.analysisId(), row.customerName(),
                        row.reportedPartNo(), row.analysisLevel(),
                        row.approvalReasons() == null ? List.of()
                                : List.of(jsonMapper.readValue(row.approvalReasons(), String[].class)),
                        row.topCauseCode(), row.topCauseName(), KoreaTime.inSeoul(row.requestedAt()), row.approvalStatus(),
                        row.severity(), row.rationale(), row.decidedBy()))
                .toList();
        return new PageResponse<>(items, repository.countApprovals(status, levels), pagination.page(),
                pagination.size());
    }

    public PageResponse<RmaTriageReviewResponse> getTriageReviews(Role role, Integer page, Integer size) {
        RoleAccess.require(role, Role.QUALITY);
        var pagination = PageParameters.resolve(page, size);
        var items = repository.findTriageReviews(pagination.offset(), pagination.size()).stream()
                .map(row -> new RmaTriageReviewResponse(row.rmaId(), KoreaTime.inSeoul(row.openedAt()),
                        row.customerName(), row.reportedPartNo(),
                        row.minorName(), row.proposal() == null ? null : jsonMapper.readValue(row.proposal(), Proposal.class)))
                .toList();
        return new PageResponse<>(items, repository.countTriageReviews(), pagination.page(), pagination.size());
    }
}
