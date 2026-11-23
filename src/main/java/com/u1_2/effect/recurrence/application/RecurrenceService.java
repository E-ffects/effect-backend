package com.u1_2.effect.recurrence.application;

import com.u1_2.effect.global.dto.PageParameters;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.global.time.WeekRange;
import com.u1_2.effect.recurrence.domain.RecurrenceErrorCode;
import com.u1_2.effect.recurrence.domain.RecurrenceRepository;
import com.u1_2.effect.recurrence.domain.RecurrenceRepository.ActionRef;
import com.u1_2.effect.recurrence.domain.RecurrenceRepository.DailyTrend;
import com.u1_2.effect.recurrence.presentation.dto.CorrectiveActionTrendResponse;
import com.u1_2.effect.recurrence.presentation.dto.CorrectiveActionTrendResponse.Check;
import com.u1_2.effect.recurrence.presentation.dto.CorrectiveActionTrendResponse.Point;
import com.u1_2.effect.recurrence.presentation.dto.CorrectiveActionsResponse;
import com.u1_2.effect.recurrence.presentation.dto.GetCorrectiveActionsRequest;
import com.u1_2.effect.recurrence.presentation.dto.GetRecurrenceReviewsRequest;
import com.u1_2.effect.recurrence.presentation.dto.RecurrenceReviewsResponse;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RecurrenceService {

    private static final int WEEKS_BEFORE = 8;
    private static final int WEEKS_AFTER = 16;

    private final RecurrenceRepository repository;
    private final Clock appClock;

    public CorrectiveActionsResponse actions(GetCorrectiveActionsRequest request, Role role) {
        RoleAccess.require(role, Role.QUALITY);
        var page = PageParameters.resolve(request.page(), request.size(), 100);
        var investigationId = optional(request.investigationId());
        var items = repository.findActions(investigationId, page, KoreaTime.startOfDay(KoreaTime.today(appClock).plusDays(1))).stream()
                .map(a -> new CorrectiveActionsResponse.Item(a.correctiveActionId(), a.investigationId(),
                        a.investigationTitle(), a.actionRole(), a.actionCategory(), a.description(), a.ownerDept(),
                        a.causeCode(), KoreaTime.inSeoul(a.selectedAt()), KoreaTime.inSeoul(a.dueAt()),
                        KoreaTime.inSeoul(a.implementedAt()), a.verificationStatus(), a.recurrenceCount()))
                .toList();
        return new CorrectiveActionsResponse(items, repository.countActions(investigationId), page.page(), page.size());
    }

    public RecurrenceReviewsResponse reviews(GetRecurrenceReviewsRequest request, Role role) {
        RoleAccess.require(role, Role.QUALITY);
        var page = PageParameters.resolve(request.page(), request.size());
        var actionId = optional(request.correctiveActionId());
        if (actionId != null) {
            action(actionId);
        }
        var verdict = optional(request.verdict());
        var items = repository.findReviews(actionId, verdict, page).stream()
                .map(r -> new RecurrenceReviewsResponse.Item(r.reviewId(), r.correctiveActionId(), r.rmaId(),
                        r.rmaItemId(), r.lotProducedAfterAction(), r.symptomMatch(), r.causeMatch(), r.verdict(),
                        KoreaTime.inSeoul(r.reviewedAt())))
                .toList();
        return new RecurrenceReviewsResponse(items, repository.countReviews(actionId, verdict), page.page(), page.size());
    }

    public CorrectiveActionTrendResponse trend(String actionId, Role role) {
        RoleAccess.require(role, Role.QUALITY);
        var action = action(actionId);
        var today = KoreaTime.today(appClock);
        var implementedAt = KoreaTime.inSeoul(action.implementedAt());
        var completedDt = implementedAt == null || implementedAt.toLocalDate().isAfter(today)
                ? null : implementedAt.toLocalDate();

        // 시행일이 새 주의 시작이므로 시행일이 섞인 전후 비교 구간이 생기지 않음
        var from = completedDt == null ? today.plusDays(1).minusWeeks(WEEKS_BEFORE) : completedDt.minusWeeks(WEEKS_BEFORE);
        var afterLimit = completedDt == null ? today : completedDt.plusWeeks(WEEKS_AFTER).minusDays(1);
        var to = afterLimit.isBefore(today) ? afterLimit : today;
        var daily = repository.findDailyTrend(actionId, action.investigationId(), KoreaTime.startOfDay(from),
                KoreaTime.startOfDay(to.plusDays(1)), KoreaTime.startOfDay(today.plusDays(1))).stream()
                .collect(Collectors.toMap(DailyTrend::day, item -> item));
        var points = WeekRange.between(from, to).stream().map(week -> point(week, daily, completedDt)).toList();
        var checks = repository.findChecks(actionId).stream()
                .map(c -> new Check(c.checkId(), c.metric(), c.baselineValue(), c.targetValue(), c.postValue(),
                        KoreaTime.inSeoul(c.windowFrom()), KoreaTime.inSeoul(c.windowTo()), c.result(),
                        KoreaTime.inSeoul(c.checkedAt())))
                .toList();
        return new CorrectiveActionTrendResponse(action.correctiveActionId(), action.investigationId(), implementedAt, completedDt,
                points, average(points, false), average(points, true), recentTrend(points), checks);
    }

    private Point point(WeekRange week, Map<LocalDate, DailyTrend> daily, LocalDate completedDt) {
        var days = week.dates().map(daily::get).filter(Objects::nonNull).toList();
        long count = days.stream().mapToLong(DailyTrend::count).sum();
        boolean after = completedDt != null && !week.from().isBefore(completedDt);
        return new Point(week.from(), week.to(), week.label(), count, after, days.stream().anyMatch(DailyTrend::recurrence));
    }

    private Double average(List<Point> points, boolean after) {
        var average = points.stream().filter(p -> p.after() == after).mapToLong(Point::count).average();
        return average.isPresent() ? Math.round(average.getAsDouble() * 100) / 100.0 : null;
    }

    private String recentTrend(List<Point> points) {
        if (points.size() < 3) {
            return null;
        }
        long first = points.get(points.size() - 3).count();
        long last = points.getLast().count();
        return last > first ? "증가" : last < first ? "감소" : "유지";
    }

    private ActionRef action(String id) {
        return repository.findAction(id).orElseThrow(() -> new ApiException(RecurrenceErrorCode.CORRECTIVE_ACTION_NOT_FOUND));
    }

    private String optional(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
