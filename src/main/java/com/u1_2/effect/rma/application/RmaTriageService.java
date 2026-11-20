package com.u1_2.effect.rma.application;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.AuthErrorCode;
import com.u1_2.effect.rma.presentation.dto.RmaTriageUpdateResponse;
import com.u1_2.effect.rma.presentation.dto.UpdateRmaTriageRequest;
import com.u1_2.effect.user.application.AuthService;
import com.u1_2.effect.user.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 로그인한 품질관리자의 이름으로 판정을 남기고 필요한 경우 분석을 시작함.
 */
@Service
@Profile({"local", "test"})
@ConditionalOnProperty(name = "ai.mode", havingValue = "mock", matchIfMissing = true)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaTriageService {

    private final AuthService authService;
    private final RmaTriageStateService stateService;
    private final RmaReanalysisService reanalysisService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RmaTriageUpdateResponse update(String rmaId, UpdateRmaTriageRequest request, AuthUser user) {
        if (user.role() != Role.QUALITY) {
            throw new ApiException(AuthErrorCode.ACCESS_DENIED);
        }
        String editor = authService.getCurrentUser(user.id()).getName();
        // 품질관리자 판정을 먼저 저장해야 AI가 새 QA 결과를 읽을 수 있음
        if (stateService.save(rmaId, request, editor)) {
            reanalysisService.startAfterTriage(rmaId);
        }
        return stateService.response(rmaId);
    }
}
