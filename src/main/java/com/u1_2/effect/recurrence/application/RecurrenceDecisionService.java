package com.u1_2.effect.recurrence.application;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.AuthErrorCode;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.recurrence.domain.RecurrenceDecision;
import com.u1_2.effect.recurrence.domain.RecurrenceDecisionRepository;
import com.u1_2.effect.recurrence.domain.RecurrenceErrorCode;
import com.u1_2.effect.recurrence.presentation.dto.RecurrenceDecisionResponse;
import com.u1_2.effect.rma.domain.RmaClaimRepository;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import com.u1_2.effect.user.domain.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecurrenceDecisionService {

    private final RecurrenceDecisionRepository decisions;
    private final RmaClaimRepository claims;
    private final UserRepository users;
    private final Clock appClock;

    @Transactional
    public RecurrenceDecisionResponse decide(String rmaId, RecurrenceDecision decision, AuthUser user) {
        RoleAccess.require(user.role(), Role.QUALITY);
        claims.requireExists(rmaId);
        var reviewer = users.findByIdOrThrow(user.id(), AuthErrorCode.INVALID_TOKEN);
        decisions.insert(rmaId, decision, reviewer.getId(), reviewer.getName(), OffsetDateTime.now(appClock));
        var saved = decisions.find(rmaId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.INTERNAL_ERROR));

        // 같은 값의 요청을 다시 보내면 원래 판정자·시간을 돌려주고 다른 값은 거절함
        if (saved.decision() != decision) {
            throw new ApiException(RecurrenceErrorCode.RECURRENCE_ALREADY_DECIDED);
        }
        return new RecurrenceDecisionResponse(rmaId, saved.decision(), saved.decidedBy(), KoreaTime.inSeoul(saved.decidedAt()));
    }
}
