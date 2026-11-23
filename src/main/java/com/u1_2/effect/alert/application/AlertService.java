package com.u1_2.effect.alert.application;

import com.u1_2.effect.alert.domain.AlertErrorCode;
import com.u1_2.effect.alert.domain.AlertRepository;
import com.u1_2.effect.alert.domain.AlertRepository.Change;
import com.u1_2.effect.alert.presentation.dto.AlertsResponse;
import com.u1_2.effect.alert.presentation.dto.GetAlertsRequest;
import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertService {

    private static final List<String> SCREEN_TYPES = List.of("고위험", "재발 의심", "확산형", "급증", "반복 재발");

    private final AlertRepository repository;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AlertsResponse list(GetAlertsRequest request, AuthUser user) {
        RoleAccess.require(user.role(), Role.QUALITY);
        String type = StringUtils.hasText(request.type()) ? request.type() : null;
        int limit = request.limit() == null ? 50 : request.limit();
        var items = repository.list(user.id(), type, Boolean.TRUE.equals(request.starred()), request.afterId(), limit)
                .stream().map(this::toItem).toList();
        var counts = repository.counts(user.id());

        // 현재 탭 필터와 무관하게 알림 벨과 각 탭의 미확인 수를 같은 스냅샷에서 계산함
        var unreadByType = new LinkedHashMap<String, Long>();
        SCREEN_TYPES.forEach(key -> unreadByType.put(key, 0L));
        counts.forEach(count -> unreadByType.put(count.type(), count.unread()));
        return new AlertsResponse(items, counts.stream().mapToLong(AlertRepository.Counts::unread).sum(),
                unreadByType, repository.maxId(), counts.stream().mapToLong(AlertRepository.Counts::starred).sum());
    }

    @Transactional
    public AlertsResponse.Item set(long id, Change change, AuthUser user) {
        changeOne(id, change, user);
        return repository.find(user.id(), id).map(this::toItem)
                .orElseThrow(() -> new ApiException(AlertErrorCode.ALERT_NOT_FOUND));
    }

    @Transactional
    public void delete(long id, AuthUser user) {
        changeOne(id, Change.DELETE, user);
    }

    @Transactional
    public int bulk(List<Long> ids, Change change, AuthUser user) {
        RoleAccess.require(user.role(), Role.QUALITY);
        return ids.isEmpty() ? 0 : repository.apply(user.id(), ids, change);
    }

    @Transactional
    public void readAll(AuthUser user) {
        RoleAccess.require(user.role(), Role.QUALITY);
        repository.readAll(user.id());
    }

    private void changeOne(long id, Change change, AuthUser user) {
        if (bulk(List.of(id), change, user) == 0) {
            throw new ApiException(AlertErrorCode.ALERT_NOT_FOUND);
        }
    }

    private AlertsResponse.Item toItem(AlertRepository.Item row) {
        return new AlertsResponse.Item(row.id(), row.type(), row.rmaId(), row.correctiveActionId(), row.targetLabel(),
                row.reason(), row.checkItems(), KoreaTime.inSeoul(row.createdAt()), row.read(), row.starred());
    }
}
