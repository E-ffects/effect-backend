package com.u1_2.effect.rma.domain;

import com.u1_2.effect.global.exception.ApiException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RmaClaimRepository {

    private final JdbcClient jdbcClient;

    public boolean exists(String rmaId) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM pcb.rma_claim WHERE rma_id = :rmaId)")
                .param("rmaId", rmaId).query(Boolean.class).single();
    }

    public void requireExists(String rmaId) {
        if (!exists(rmaId)) {
            throw new ApiException(RmaErrorCode.RMA_NOT_FOUND);
        }
    }

    public Optional<ClaimStatus> findForUpdate(String rmaId) {
        // QA와 CS가 같은 접수를 바꿀 때 같은 행을 잠가 판정·실행 순서를 맞춤
        return jdbcClient.sql("""
                SELECT customer_id, cs_status, qa_status FROM pcb.rma_claim WHERE rma_id = :rmaId FOR UPDATE
                """).param("rmaId", rmaId).query(ClaimStatus.class).optional();
    }

    public ClaimStatus requireForUpdate(String rmaId) {
        return findForUpdate(rmaId).orElseThrow(() -> new ApiException(RmaErrorCode.RMA_NOT_FOUND));
    }

    public void updateQaStatus(String rmaId, String status) {
        jdbcClient.sql("UPDATE pcb.rma_claim SET qa_status = :status WHERE rma_id = :rmaId")
                .param("rmaId", rmaId).param("status", status).update();
    }

    public void updateCsStatus(String rmaId, String status) {
        jdbcClient.sql("UPDATE pcb.rma_claim SET cs_status = :status WHERE rma_id = :rmaId")
                .param("rmaId", rmaId).param("status", status).update();
    }

    public record ClaimStatus(String customerId, String csStatus, String qaStatus) {
    }
}
