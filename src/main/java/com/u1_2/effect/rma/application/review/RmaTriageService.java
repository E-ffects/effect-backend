package com.u1_2.effect.rma.application.review;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.rma.application.analysis.RmaAnalysisRegistrationService;
import com.u1_2.effect.rma.application.intake.IntakeWorkflowStateService;
import com.u1_2.effect.rma.presentation.dto.RmaTriageUpdateResponse;
import com.u1_2.effect.rma.presentation.dto.UpdateRmaTriageRequest;
import com.u1_2.effect.user.application.AuthService;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 로그인한 품질관리자의 이름으로 판정을 남기고 필요한 경우 분석을 시작함.
 */
@Slf4j
@Service
@Profile({"local", "test", "prod"})
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaTriageService {

    private final AuthService authService;
    private final RmaTriageStateService stateService;
    private final RmaAnalysisRegistrationService analysisRegistrationService;
    private final IntakeWorkflowStateService intakeStateService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RmaTriageUpdateResponse update(String rmaId, UpdateRmaTriageRequest request, AuthUser user) {
        RoleAccess.require(user.role(), Role.QUALITY);
        String editor = authService.getCurrentUser(user.id()).getName();
        // 품질관리자 판정을 먼저 저장해야 AI가 새 QA 결과를 읽을 수 있음
        if (stateService.save(rmaId, request, editor)) {
            try {
                analysisRegistrationService.startAfterTriage(rmaId);
            } catch (RuntimeException e) {
                // 판정은 이미 저장됐으므로 실패 상태로 응답하고 재처리 API로 분석 등록을 이어가게 함
                log.error("판정 후 분석 등록 실패 rmaId={}", rmaId, e);
                intakeStateService.failUnexpected(rmaId);
            }
        }
        return stateService.response(rmaId);
    }
}
