package com.u1_2.effect.dashboard.application;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.dashboard.domain.DashboardLayoutRepository;
import com.u1_2.effect.dashboard.domain.DashboardWidget;
import com.u1_2.effect.dashboard.presentation.dto.DashboardLayoutResponse;
import com.u1_2.effect.dashboard.presentation.dto.SaveDashboardLayoutRequest;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardLayoutService {

    private static final String QUALITY = "quality";
    private static final List<DashboardWidget> DEFAULT_LAYOUT = List.of(
            new DashboardWidget("defectTop5", "quarter", false),
            new DashboardWidget("repeatRank", "quarter", false),
            new DashboardWidget("trend", "half", false),
            new DashboardWidget("priority", "full", false));
    private static final List<String> WIDGET_IDS = DEFAULT_LAYOUT.stream().map(DashboardWidget::id).toList();

    private final DashboardLayoutRepository repository;

    public DashboardLayoutResponse getQuality(AuthUser user) {
        RoleAccess.require(user.role(), Role.QUALITY);
        var saved = new LinkedHashMap<String, DashboardWidget>();
        repository.findByUserId(user.id(), QUALITY).stream().filter(w -> WIDGET_IDS.contains(w.id()))
                .forEach(w -> saved.put(w.id(), w));

        // 화면이 업데이트돼도 저장 순서는 유지하고 새 위젯은 기본 설정으로 뒤에 붙임
        DEFAULT_LAYOUT.forEach(w -> saved.putIfAbsent(w.id(), w));
        return new DashboardLayoutResponse(List.copyOf(saved.values()), DEFAULT_LAYOUT);
    }

    @Transactional
    public void saveQuality(SaveDashboardLayoutRequest request, AuthUser user) {
        RoleAccess.require(user.role(), Role.QUALITY);
        var ids = request.layout().stream().map(DashboardWidget::id).collect(Collectors.toSet());
        // 순서·숨김 상태를 완전히 복원할 수 있도록 현재 위젯을 빠짐없이 한 번씩 받음
        if (request.layout().size() != WIDGET_IDS.size() || !ids.containsAll(WIDGET_IDS)) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
        repository.save(user.id(), QUALITY, request.layout());
    }
}
