package com.u1_2.effect.patternreport.application;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.dto.PageParameters;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.time.KoreaDateRange;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.pattern.domain.PatternRepository.Selection;
import com.u1_2.effect.patternreport.domain.PatternReportBody;
import com.u1_2.effect.patternreport.domain.PatternReportErrorCode;
import com.u1_2.effect.patternreport.domain.PatternReportRepository;
import com.u1_2.effect.patternreport.domain.PatternReportRepository.Scope;
import com.u1_2.effect.patternreport.domain.PatternReportRepository.Stored;
import com.u1_2.effect.patternreport.presentation.dto.CreatePatternDeliveryRequest;
import com.u1_2.effect.patternreport.presentation.dto.CreatePatternReportRequest;
import com.u1_2.effect.patternreport.presentation.dto.PatternDeliveryResponse;
import com.u1_2.effect.patternreport.presentation.dto.PatternReportResponse;
import com.u1_2.effect.patternreport.presentation.dto.PatternReportResponse.Header;
import com.u1_2.effect.patternreport.presentation.dto.PatternReportResponse.History;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatternReportService {

    private final PatternReportDraftFactory drafts;
    private final PatternReportRepository repository;
    private final PatternReportDelivery delivery;
    private final JsonMapper json;
    private final Clock appClock;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PatternReportResponse create(String patternId, CreatePatternReportRequest request, AuthUser user) {
        RoleAccess.require(user.role(), Role.QUALITY);
        var range = KoreaDateRange.resolve(request.from(), request.to(), KoreaTime.today(appClock), 90);
        range.requireAtMostDays(367);
        String product = filter(request.product());
        var scope = new Scope(patternId, range.from(), range.to(), product == null ? "" : product);
        var saved = repository.findScope(scope);
        if (saved.isPresent()) {
            return response(saved.get());
        }
        // 집계 트랜잭션을 닫은 후 한 번의 INSERT로 저장해 동시 생성 시에도 같은 보고서를 돌려줌
        var draft = drafts.build(new Selection(range.start(), range.end(), product, patternId));
        boolean inserted = repository.insert(scope, draft.title(), user.id(), KoreaTime.inSeoul(OffsetDateTime.now(appClock)),
                json.writeValueAsString(draft.body()));
        var report = repository.findScope(scope).orElseThrow(this::notFound);
        if (inserted) {
            log.info("패턴 보고서 저장 완료 reportId={} patternId={}", report.header().reportId(), patternId);
        }
        return response(report);
    }

    public History history(String patternId, Integer page, Integer size, Role role) {
        RoleAccess.require(role, Role.QUALITY);
        String id = filter(patternId);
        var pagination = PageParameters.resolve(page, size);
        var items = repository.list(id, pagination).stream().map(this::header).toList();
        return new History(items, repository.count(id), pagination.page(), pagination.size());
    }

    public PatternReportResponse get(long reportId, Role role) {
        RoleAccess.require(role, Role.QUALITY);
        return response(find(reportId));
    }

    public PatternDeliveryResponse preview(long reportId, CreatePatternDeliveryRequest request, Role role) {
        RoleAccess.require(role, Role.QUALITY);
        var saved = find(reportId);
        return delivery.preview(header(saved.header()), body(saved), request);
    }

    private Stored find(long reportId) {
        return repository.find(reportId).orElseThrow(this::notFound);
    }

    private PatternReportResponse response(Stored saved) {
        var report = body(saved);
        return new PatternReportResponse(header(saved.header()), report, delivery.departments(report));
    }

    private PatternReportBody body(Stored saved) {
        return json.readValue(saved.body(), PatternReportBody.class);
    }

    private Header header(PatternReportRepository.Header row) {
        return new Header(row.reportId(), row.patternId(), row.title(), row.scopeFrom(), row.scopeTo(), row.product(),
                row.generatedBy(), KoreaTime.inSeoul(row.generatedAt()));
    }

    private String filter(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private ApiException notFound() {
        return new ApiException(PatternReportErrorCode.PATTERN_REPORT_NOT_FOUND);
    }
}
