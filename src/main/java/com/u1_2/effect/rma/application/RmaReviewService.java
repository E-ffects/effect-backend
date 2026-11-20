package com.u1_2.effect.rma.application;

import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.AuthErrorCode;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.rma.domain.RmaReviewRepository;
import com.u1_2.effect.rma.domain.RmaReviewRepository.ApprovalLevel;
import com.u1_2.effect.rma.domain.RmaReviewRepository.ApprovalStatus;
import com.u1_2.effect.rma.presentation.dto.RmaReviewResponse;
import com.u1_2.effect.rma.presentation.dto.RmaReviewResponse.Approval;
import com.u1_2.effect.rma.presentation.dto.RmaReviewResponse.Proposal;
import com.u1_2.effect.rma.presentation.dto.RmaReviewResponse.Triage;
import com.u1_2.effect.user.domain.Role;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RmaReviewRepository repository;
    private final JsonMapper jsonMapper;

    public RmaReviewResponse<Approval> getApprovals(Role role, ApprovalStatus status, ApprovalLevel level,
                                                    int page, int size) {
        long offset = pageOffset(role, page, size);
        var items = repository.findApprovals(status, level.levels(), offset, size).stream()
                .map(row -> new Approval(row.rmaId(), row.analysisId(), row.analysisLevel(),
                        row.approvalReasons() == null ? List.of()
                                : List.of(jsonMapper.readValue(row.approvalReasons(), String[].class)),
                        row.topCauseCode(), row.topCauseName(), inSeoul(row.requestedAt()), row.approvalStatus(),
                        row.severity(), row.rationale(), row.decidedBy()))
                .toList();
        return new RmaReviewResponse<>(items, repository.countApprovals(status, level.levels()), page, size);
    }

    public RmaReviewResponse<Triage> getTriageReviews(Role role, int page, int size) {
        long offset = pageOffset(role, page, size);
        var items = repository.findTriageReviews(offset, size).stream()
                .map(row -> new Triage(row.rmaId(), inSeoul(row.openedAt()), row.customerName(), row.reportedPartNo(),
                        row.minorName(), row.proposal() == null ? null : jsonMapper.readValue(row.proposal(), Proposal.class)))
                .toList();
        return new RmaReviewResponse<>(items, repository.countTriageReviews(), page, size);
    }

    private long pageOffset(Role role, int page, int size) {
        if (role != Role.QUALITY) {
            throw new ApiException(AuthErrorCode.ACCESS_DENIED);
        }
        if (page < 1 || size < 1 || size > 100) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
        return (page - 1L) * size;
    }

    private OffsetDateTime inSeoul(OffsetDateTime time) {
        return time.atZoneSameInstant(SEOUL).toOffsetDateTime();
    }
}
