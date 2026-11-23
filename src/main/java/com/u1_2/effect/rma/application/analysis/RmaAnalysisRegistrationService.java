package com.u1_2.effect.rma.application.analysis;

import com.u1_2.effect.config.ai.AiClient;
import com.u1_2.effect.config.ai.AiClient.AnalysisRegistration;
import com.u1_2.effect.config.ai.AiClient.AnalysisRequest;
import com.u1_2.effect.config.ai.AiErrorCode;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.rma.application.intake.IntakeWorkflowStateService;
import com.u1_2.effect.rma.domain.RmaClaimRepository;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository;
import com.u1_2.effect.rma.domain.analysis.RmaAnalysisRepository.ReanalysisInput;
import com.u1_2.effect.rma.presentation.analysis.dto.CreateRmaAnalysisRequest;
import com.u1_2.effect.rma.presentation.analysis.dto.RmaAnalysisRegistrationResponse;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 분석 등록은 잠금 안에서 상태를 확인하고 등록 선점을 커밋한 뒤 트랜잭션 밖에서 FastAPI를 호출함.
 * 결과는 짧은 트랜잭션으로 저장하고 선점을 지움. 호출이 실패하면 선점만 지우고 예외를 호출부에 넘김.
 */
@Service
@Profile({"local", "test", "prod"})
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaAnalysisRegistrationService {

    private static final String DEFAULT_MODE = "B";
    private static final int DEFAULT_TOP_K = 3;
    /** AI 등록 응답 대기(최대 5초)보다 길게 잡아 호출 도중 선점이 풀리지 않게 함 */
    private static final int LEASE_SECONDS = 30;
    /** 같은 RMA를 먼저 등록 중인 요청을 기다리는 최대 시간과 확인 간격 */
    private static final Duration WAIT_FOR_OTHER = Duration.ofSeconds(10);
    private static final Duration WAIT_INTERVAL = Duration.ofMillis(100);

    private final RmaClaimRepository claimRepository;
    private final RmaAnalysisRepository repository;
    private final IntakeWorkflowStateService stateService;
    private final AiClient aiClient;
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RmaAnalysisRegistrationResponse register(String rmaId, Role role, CreateRmaAnalysisRequest request) {
        RoleAccess.require(role, Role.QUALITY);
        String mode = request == null || request.mode() == null ? DEFAULT_MODE : request.mode().name();
        int topK = request == null || request.topK() == null ? DEFAULT_TOP_K : request.topK();

        long deadline = System.nanoTime() + WAIT_FOR_OTHER.toNanos();
        while (true) {
            var prepared = transactionTemplate.execute(status -> prepareRegister(rmaId));
            if (prepared.running() != null) {
                return prepared.running();
            }
            if (prepared.leaseUntil() != null) {
                return queue(rmaId, prepared.input(), mode, topK, prepared.leaseUntil());
            }
            // 같은 RMA를 다른 요청이 등록 중이면 끝날 때까지 기다렸다가 그 분석을 돌려줌
            if (System.nanoTime() > deadline) {
                throw new ApiException(RmaErrorCode.ANALYSIS_REGISTERING);
            }
            sleep();
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void startAfterTriage(String rmaId) {
        long deadline = System.nanoTime() + WAIT_FOR_OTHER.toNanos();
        while (true) {
            // 접수 또는 QA 확인이 끝난 뒤 잠금 안에서 아직 분석을 시작할 상태인지 다시 확인함
            var prepared = transactionTemplate.execute(status -> prepareStart(rmaId));
            if (prepared == null) {
                return;
            }
            if (prepared.leaseUntil() != null) {
                start(rmaId, prepared);
                return;
            }
            // 같은 RMA를 다른 요청이 등록 중이면 끝난 상태로 응답하도록 기다림
            if (System.nanoTime() > deadline) {
                return;
            }
            sleep();
        }
    }

    private Prepared prepareStart(String rmaId) {
        var input = loadForRegistration(rmaId);
        if (!"PROCESSING".equals(input.intakeState()) || !input.triaged() || input.decisionFinalized()) {
            return null;
        }
        return new Prepared(input, null, repository.claimRegistration(rmaId, LEASE_SECONDS).orElse(null));
    }

    private void start(String rmaId, Prepared prepared) {
        try {
            queue(rmaId, prepared.input(), DEFAULT_MODE, DEFAULT_TOP_K, prepared.leaseUntil());
        } catch (ApiException e) {
            if (e.getErrorCode() != AiErrorCode.AI_UNAVAILABLE) {
                throw e;
            }
            stateService.fail(rmaId, DEFAULT_MODE, "AI 원인분석을 등록하지 못했습니다.");
        }
    }

    private Prepared prepareRegister(String rmaId) {
        var input = loadForRegistration(rmaId);
        if (input.decisionFinalized()) {
            throw new ApiException(RmaErrorCode.CAUSE_ALREADY_DECIDED);
        }
        var active = repository.findActiveAnalysis(rmaId);
        if (active.isPresent()) {
            var running = active.get();
            return new Prepared(input, new RmaAnalysisRegistrationResponse(rmaId, running.analysisId(),
                    running.analysisState()), null);
        }
        if (!input.triaged() || (input.intakeState() != null && !"ACCEPTED".equals(input.intakeState()))) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
        return new Prepared(input, null, repository.claimRegistration(rmaId, LEASE_SECONDS).orElse(null));
    }

    private ReanalysisInput loadForRegistration(String rmaId) {
        claimRepository.requireForUpdate(rmaId);
        return repository.findReanalysisInput(rmaId).orElseThrow(() -> new ApiException(RmaErrorCode.RMA_NOT_FOUND));
    }

    private RmaAnalysisRegistrationResponse queue(String rmaId, ReanalysisInput input, String mode, int topK,
                                                  OffsetDateTime leaseUntil) {
        AnalysisRegistration registration;
        try {
            registration = aiClient.registerAnalysis(
                    new AnalysisRequest(rmaId, mode, topK, true, input.rawText(), input.claimedDefectQty()));
        } catch (RuntimeException e) {
            repository.releaseRegistration(rmaId, leaseUntil);
            throw e;
        }
        return transactionTemplate.execute(status -> {
            claimRepository.requireForUpdate(rmaId);
            String state = stateService.saveQueuedAnalysis(rmaId, mode, registration);
            repository.releaseRegistration(rmaId, leaseUntil);
            return new RmaAnalysisRegistrationResponse(rmaId, registration.analysisId(), state);
        });
    }

    private static void sleep() {
        try {
            Thread.sleep(WAIT_INTERVAL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(RmaErrorCode.ANALYSIS_REGISTERING);
        }
    }

    /** 실행 중인 분석이 있으면 running, 등록을 선점했으면 leaseUntil, 둘 다 없으면 다른 요청이 등록 중 */
    private record Prepared(ReanalysisInput input, RmaAnalysisRegistrationResponse running, OffsetDateTime leaseUntil) {
    }
}
