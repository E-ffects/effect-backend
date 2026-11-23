package com.u1_2.effect.recurrence.application;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.AuthErrorCode;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.recurrence.domain.CorrectiveActionPlan;
import com.u1_2.effect.recurrence.domain.CorrectiveActionRepository;
import com.u1_2.effect.recurrence.domain.CorrectiveActionRepository.Detail;
import com.u1_2.effect.recurrence.domain.RecurrenceErrorCode;
import com.u1_2.effect.recurrence.presentation.dto.CorrectiveActionDetailResponse;
import com.u1_2.effect.recurrence.presentation.dto.SaveCorrectiveActionRequest;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import com.u1_2.effect.user.domain.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CorrectiveActionService {

    private final CorrectiveActionRepository repository;
    private final UserRepository users;
    private final JsonMapper jsonMapper;
    private final Clock appClock;

    public CorrectiveActionDetailResponse get(String id, AuthUser user) {
        RoleAccess.require(user.role(), Role.QUALITY);
        return response(find(id));
    }

    @Transactional
    public CorrectiveActionDetailResponse create(
            String investigationId, String key, SaveCorrectiveActionRequest request, AuthUser user
    ) {
        RoleAccess.require(user.role(), Role.QUALITY);
        var plan = toPlan(request);
        var actor = users.findByIdOrThrow(user.id(), AuthErrorCode.INVALID_TOKEN);
        String requestKey = StringUtils.hasText(key) ? key.strip() : UUID.randomUUID().toString();
        String id = "CA-" + UUID.randomUUID();
        var payload = jsonMapper.createObjectNode().put("investigation_id", investigationId);
        payload.set("plan", jsonMapper.valueToTree(plan));
        String serialized = payload.toString();
        if (!repository.reserve(actor.getId(), requestKey, id, serialized)) {
            return existingRegistration(actor.getId(), requestKey, serialized);
        }

        if (!repository.investigationExists(investigationId)) {
            throw new ApiException(RecurrenceErrorCode.INVESTIGATION_NOT_FOUND);
        }
        var selectedAt = now();
        validatePlan(plan, selectedAt);
        repository.create(id, investigationId, plan, selectedAt);
        return response(find(id));
    }

    @Transactional
    public CorrectiveActionDetailResponse update(String id, SaveCorrectiveActionRequest request, AuthUser user) {
        RoleAccess.require(user.role(), Role.QUALITY);
        var current = lock(id);
        requireWritable(current);
        var plan = toPlan(request);
        validatePlan(plan, current.selectedAt());
        repository.update(id, plan);
        return response(find(id));
    }

    @Transactional
    public CorrectiveActionDetailResponse implement(String id, OffsetDateTime requestedAt, AuthUser user) {
        RoleAccess.require(user.role(), Role.QUALITY);
        var current = lock(id);
        var implementedAt = canonical(requestedAt);
        if (current.implementedAt() != null && current.implementedAt().isEqual(implementedAt)) {
            return response(current);
        }
        requireWritable(current);
        if (implementedAt.isBefore(current.selectedAt()) || implementedAt.isAfter(now())) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
        repository.implement(id, implementedAt);
        return response(find(id));
    }

    private CorrectiveActionDetailResponse existingRegistration(long userId, String key, String payload) {
        var registration = repository.findRegistration(userId, key, payload)
                .orElseThrow(() -> new ApiException(CommonErrorCode.INTERNAL_ERROR));
        if (!registration.sameRequest()) {
            throw new ApiException(RecurrenceErrorCode.CORRECTIVE_ACTION_REQUEST_CONFLICT);
        }
        return response(find(registration.correctiveActionId()));
    }

    private void requireWritable(Detail current) {
        // 시행 전 계획만 수정함. 기존 검증·재발 이력의 기준 대책이 바뀌지 않게 함
        if (current.implementedAt() != null || !"IMPLEMENTING".equals(current.verificationStatus())
                || repository.hasHistory(current.correctiveActionId())) {
            throw new ApiException(RecurrenceErrorCode.CORRECTIVE_ACTION_LOCKED);
        }
    }

    private void validatePlan(CorrectiveActionPlan plan, OffsetDateTime selectedAt) {
        if (plan.dueAt().isBefore(selectedAt) || !repository.codesExist(plan)) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private CorrectiveActionPlan toPlan(SaveCorrectiveActionRequest request) {
        var scope = request.scope() == null || request.scope().isNull() ? jsonMapper.createObjectNode() : request.scope();
        if (!scope.isObject() || scope.toString().length() > 16000) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
        return new CorrectiveActionPlan(request.actionRole(), request.actionCategory(), request.description().strip(),
                request.ownerDept().strip(), optional(request.causeCode()), optional(request.escapeCode()),
                canonical(request.dueAt()), scope);
    }

    private Detail find(String id) {
        return repository.find(id).orElseThrow(() -> new ApiException(RecurrenceErrorCode.CORRECTIVE_ACTION_NOT_FOUND));
    }

    private Detail lock(String id) {
        return repository.lock(id).orElseThrow(() -> new ApiException(RecurrenceErrorCode.CORRECTIVE_ACTION_NOT_FOUND));
    }

    private CorrectiveActionDetailResponse response(Detail detail) {
        return new CorrectiveActionDetailResponse(detail.correctiveActionId(), detail.investigationId(), detail.actionRole(),
                detail.actionCategory(), detail.description(), detail.ownerDept(), detail.causeCode(), detail.escapeCode(),
                KoreaTime.inSeoul(detail.selectedAt()), KoreaTime.inSeoul(detail.dueAt()), KoreaTime.inSeoul(detail.implementedAt()),
                detail.verificationStatus(), jsonMapper.readTree(detail.scope()));
    }

    private OffsetDateTime now() {
        return canonical(OffsetDateTime.now(appClock));
    }

    private OffsetDateTime canonical(OffsetDateTime value) {
        return KoreaTime.inSeoul(value).truncatedTo(ChronoUnit.SECONDS);
    }

    private String optional(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}
