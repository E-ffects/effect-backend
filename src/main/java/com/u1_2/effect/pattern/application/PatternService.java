package com.u1_2.effect.pattern.application;

import com.u1_2.effect.global.dto.PageParameters;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.time.KoreaDateRange;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.investigation.application.InvestigationRankingService;
import com.u1_2.effect.investigation.application.InvestigationRankingService.Ranked;
import com.u1_2.effect.investigation.domain.InvestigationRankingRepository.Investigation;
import com.u1_2.effect.pattern.domain.PatternErrorCode;
import com.u1_2.effect.pattern.domain.PatternRepository;
import com.u1_2.effect.pattern.domain.PatternRepository.CauseCount;
import com.u1_2.effect.pattern.domain.PatternRepository.Selection;
import com.u1_2.effect.pattern.presentation.dto.GetPatternDetailRequest;
import com.u1_2.effect.pattern.presentation.dto.GetPatternListRequest;
import com.u1_2.effect.pattern.presentation.dto.PatternDetailResponse;
import com.u1_2.effect.pattern.presentation.dto.PatternDetailResponse.Action;
import com.u1_2.effect.pattern.presentation.dto.PatternDetailResponse.Equipment;
import com.u1_2.effect.pattern.presentation.dto.PatternDetailResponse.Evidence;
import com.u1_2.effect.pattern.presentation.dto.PatternDetailResponse.Lot;
import com.u1_2.effect.pattern.presentation.dto.PatternListResponse;
import com.u1_2.effect.pattern.presentation.dto.PatternListResponse.Cause;
import com.u1_2.effect.pattern.presentation.dto.PatternListResponse.Pattern;
import com.u1_2.effect.pattern.presentation.dto.PatternListResponse.Period;
import com.u1_2.effect.pattern.presentation.dto.PatternReportResponse;
import com.u1_2.effect.rma.domain.RmaSeverity;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class PatternService {

    private static final String UNRESOLVED = "미확정";

    private final PatternRepository repository;
    private final InvestigationRankingService ranking;
    private final Clock appClock;

    public PatternListResponse list(GetPatternListRequest request, Role role) {
        RoleAccess.require(role, Role.QUALITY);
        var period = period(request.from(), request.to());
        String product = productOrNull(request.product());
        var page = PageParameters.resolve(request.page(), request.size(), 100);
        var repeated = repeated(ranking.rank(period, product));
        var patterns = repeated.stream().skip(page.offset()).limit(page.size()).map(this::toPattern).toList();
        var causes = repository.findCauses(selection(period, product, null)).stream()
                .map(cause -> new Cause(Objects.requireNonNullElse(cause.cause(), UNRESOLVED), cause.count()))
                .toList();
        return new PatternListResponse(new Period(period.from(), period.to()), patterns, causes,
                repeated.size(), page.page(), page.size());
    }

    public PatternDetailResponse detail(String id, GetPatternDetailRequest request, Role role) {
        RoleAccess.require(role, Role.QUALITY);
        var period = period(request.from(), request.to());
        String product = productOrNull(request.product());
        var selection = selection(period, product, id);
        var profile = profile(selection);

        var ranked = ranking.rank(period, product);
        var selected = ranked.stream().map(Ranked::investigation)
                .filter(p -> p.investigationId().equals(id)).findFirst().orElse(null);
        var order = repeated(ranked).stream().map(Ranked::investigation).map(Investigation::investigationId).toList();
        int rankIndex = order.indexOf(id);
        var evidence = repository.findEvidence(selection).stream().map(row -> {
            var openedAt = KoreaTime.inSeoul(row.openedAt());
            return new Evidence(row.rmaId(), openedAt.toLocalDate(), openedAt,
                    RmaSeverity.from(row.severity()).label());
        }).toList();
        var resolved = repository.findCauses(selection).stream().filter(c -> c.cause() != null).toList();
        long resolvedCount = resolved.stream().mapToLong(CauseCount::count).sum();
        var primary = causeAt(resolved, 0, resolvedCount, UNRESOLVED);
        var secondary = causeAt(resolved, 1, resolvedCount, "미수집");
        var lots = repository.findLots(selection).stream().map(lot -> new Lot(lot.lot(), lot.count())).toList();
        var equipment = repository.findEquipment(selection).stream()
                .map(row -> new Equipment(row.equipmentId(), row.count())).toList();
        var actions = repository.findActions(selection).stream()
                .map(action -> new Action(action.correctiveActionId(), action.actionCategory(), action.description(),
                        action.verificationStatus(), KoreaTime.inSeoul(action.implementedAt())))
                .toList();
        return new PatternDetailResponse(profile.investigationId(), id, profile.title(), profile.status(),
                selected == null ? product : selected.product(), selected == null ? null : selected.defectType(),
                profile.analysisLevel(), rankIndex < 0 ? null : rankIndex + 1, evidence.size() - resolvedCount,
                profile.loss(), primary.name(), primary.ratio(), secondary.name(), secondary.ratio(),
                lots, equipment, evidence, actions);
    }

    public PatternReportResponse report(String id, GetPatternDetailRequest request, Role role) {
        RoleAccess.require(role, Role.QUALITY);
        var period = period(request.from(), request.to());
        var selection = selection(period, productOrNull(request.product()), id);
        var profile = profile(selection);
        var causes = repository.findCauses(selection);
        long cases = causes.stream().mapToLong(CauseCount::count).sum();
        long pending = causes.stream().filter(c -> c.cause() == null).mapToLong(CauseCount::count).sum();
        String mainCause = causes.stream().map(CauseCount::cause).filter(Objects::nonNull).findFirst().orElse(UNRESOLVED);
        var actions = repository.findActions(selection).stream().map(PatternRepository.Action::description)
                .filter(StringUtils::hasText).distinct().limit(3).toList();
        var evidence = repository.findEvidence(selection, 5).stream().map(PatternRepository.Evidence::rmaId).toList();
        // 패턴 요약은 등록된 조사 기록을 쓰고 근거 RMA는 화면에서 열 수 있도록 ID로 전달함
        return new PatternReportResponse(id, profile.title(), mainCause,
                "%s~%s 접수 %d건, 원인 미확정 %d건, 추정 손실 %,d원".formatted(
                        period.from(), period.to(), cases, pending, profile.loss()),
                actions.isEmpty() ? "조사 근거를 검토한 뒤 대책을 기록해 주세요." : String.join(" / ", actions),
                evidence, "등록된 조사·대책 기록을 바탕으로 작성한 요약입니다.");
    }

    private Pattern toPattern(Ranked ranked) {
        var row = ranked.investigation();
        var id = row.investigationId();
        return new Pattern(id, id, row.title(), row.product(), row.defectType(), row.analysisLevel(), row.status(),
                row.count(), ranked.score(), row.causeConfirmed() ? row.causeName() : UNRESOLVED);
    }

    private List<Ranked> repeated(List<Ranked> ranking) {
        return ranking.stream().filter(p -> p.investigation().count() >= 2).toList();
    }

    private KoreaDateRange period(LocalDate from, LocalDate to) {
        var period = KoreaDateRange.resolve(from, to, KoreaTime.today(appClock), 90);
        period.requireAtMostDays(367);
        return period;
    }

    private Selection selection(KoreaDateRange period, String product, String id) {
        return new Selection(period.start(), period.end(), product, id);
    }

    private PatternRepository.Profile profile(Selection selection) {
        return repository.findProfile(selection).orElseThrow(() -> new ApiException(PatternErrorCode.PATTERN_NOT_FOUND));
    }

    private String productOrNull(String product) {
        return StringUtils.hasText(product) ? product : null;
    }

    // 원인 비율은 확정된 접수만 분모에 넣고, 확정 원인이 부족하면 해당 비율을 비워 둠
    private CauseRatio causeAt(List<CauseCount> causes, int index, long total, String fallbackName) {
        if (index >= causes.size()) {
            return new CauseRatio(fallbackName, null);
        }
        var cause = causes.get(index);
        return new CauseRatio(cause.cause(), (int) Math.round(cause.count() * 100.0 / total));
    }

    private record CauseRatio(String name, Integer ratio) {
    }
}
