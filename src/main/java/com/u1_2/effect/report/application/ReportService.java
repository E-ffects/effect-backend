package com.u1_2.effect.report.application;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.dto.PageParameters;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.AuthErrorCode;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.report.domain.ReportBody;
import com.u1_2.effect.report.domain.ReportErrorCode;
import com.u1_2.effect.report.domain.ReportRepository;
import com.u1_2.effect.report.domain.ReportRepository.NewReport;
import com.u1_2.effect.report.domain.ReportRepository.Stored;
import com.u1_2.effect.report.presentation.dto.EditReportRequest;
import com.u1_2.effect.report.presentation.dto.ReportDetailResponse;
import com.u1_2.effect.report.presentation.dto.ReportDetailResponse.Draft;
import com.u1_2.effect.report.presentation.dto.ReportDetailResponse.Review;
import com.u1_2.effect.report.presentation.dto.ReportHistoryResponse;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.domain.RmaSeverity;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import com.u1_2.effect.user.domain.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository repository;
    private final ReportDraftFactory drafts;
    private final UserRepository users;
    private final JsonMapper jsonMapper;
    private final Clock appClock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReportHistoryResponse history(Integer pageNumber, Integer size, AuthUser user) {
        requireQuality(user);
        var page = PageParameters.resolve(pageNumber, size, 100);
        var items = repository.history(page).stream().map(h -> new ReportHistoryResponse.Item(
                h.rmaId(), h.product(), h.defectType(), h.claimDate(), h.severity(), h.claimStatus(),
                KoreaTime.inSeoul(h.generatedAt()), h.reviewedAt() != null, h.reviewedBy(), KoreaTime.inSeoul(h.reviewedAt())
        )).toList();
        return new ReportHistoryResponse(items, repository.count(), page.page(), page.size());
    }

    public ReportDetailResponse get(String rmaId, AuthUser user) {
        requireQuality(user);
        return response(stored(rmaId));
    }

    @Transactional
    public ReportDetailResponse create(String rmaId, AuthUser user) {
        requireQuality(user);
        var saved = repository.find(rmaId);
        if (saved.isPresent()) {
            return response(saved.get());
        }
        var draft = drafts.build(rmaId);
        var header = repository.header(rmaId).orElseThrow(() -> new ApiException(RmaErrorCode.RMA_NOT_FOUND));
        repository.create(new NewReport(rmaId, header.product(), draft.defectType(), header.claimDate(),
                RmaSeverity.from(header.severity()).label(), header.claimStatus(), jsonMapper.writeValueAsString(draft.body()), now()));
        return response(stored(rmaId));
    }

    public Draft draft(String rmaId, AuthUser user) {
        requireQuality(user);
        var draft = drafts.build(rmaId);
        return new Draft(rmaId, now(), draft.body());
    }

    @Transactional
    public ReportDetailResponse edit(String rmaId, EditReportRequest request, AuthUser user) {
        requireQuality(user);
        var saved = locked(rmaId);
        var edited = body(saved).edit(request.mainCause().strip(), request.qualityRecommendation().strip(),
                request.csRecommendation().strip(), name(user), now());
        repository.changeBody(rmaId, jsonMapper.writeValueAsString(edited), null, null);
        return response(stored(rmaId));
    }

    @Transactional
    public ReportDetailResponse replace(String rmaId, ReportBody proposed, AuthUser user) {
        requireQuality(user);
        if (!rmaId.equals(proposed.rmaId())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
        locked(rmaId);
        var source = drafts.build(rmaId);
        // 비교할 때 쓴 분석이 바뀌면 이전 초안으로 덮어쓰지 않고 다시 확인하게 함
        if (!source.body().equals(proposed)) {
            throw new ApiException(ReportErrorCode.REPORT_DRAFT_STALE);
        }
        repository.changeBody(rmaId, jsonMapper.writeValueAsString(source.body()), now(), source.defectType());
        return response(stored(rmaId));
    }

    @Transactional
    public Review review(String rmaId, boolean reviewed, AuthUser user) {
        requireQuality(user);
        locked(rmaId);
        String reviewer = reviewed ? name(user) : null;
        var time = reviewed ? now() : null;
        repository.setReview(rmaId, reviewer, time);
        return new Review(reviewed, reviewer, time);
    }

    private ReportDetailResponse response(Stored stored) {
        var h = stored.history();
        return new ReportDetailResponse(h.rmaId(), h.product(), h.defectType(), h.claimDate(), h.severity(),
                h.claimStatus(), KoreaTime.inSeoul(h.generatedAt()), h.reviewedAt() != null, h.reviewedBy(),
                KoreaTime.inSeoul(h.reviewedAt()), body(stored));
    }

    private ReportBody body(Stored stored) {
        return jsonMapper.readValue(stored.body(), ReportBody.class);
    }

    private Stored stored(String id) {
        return repository.find(id).orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
    }

    private Stored locked(String id) {
        return repository.lock(id).orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
    }

    private void requireQuality(AuthUser user) {
        RoleAccess.require(user.role(), Role.QUALITY);
    }

    private String name(AuthUser user) {
        return users.findByIdOrThrow(user.id(), AuthErrorCode.INVALID_TOKEN).getName();
    }

    private OffsetDateTime now() {
        return KoreaTime.inSeoul(OffsetDateTime.now(appClock));
    }
}
