package com.u1_2.effect.rma.application.analysis;

import com.u1_2.effect.config.ai.AiClient;
import com.u1_2.effect.config.ai.AiClient.SimilarCase;
import com.u1_2.effect.config.ai.AiUnavailableException;
import com.u1_2.effect.global.time.KoreaTime;
import com.u1_2.effect.rma.domain.RmaClaimRepository;
import com.u1_2.effect.rma.presentation.analysis.dto.RmaSimilarResponse;
import com.u1_2.effect.rma.presentation.analysis.dto.RmaSimilarResponse.Action;
import com.u1_2.effect.rma.presentation.analysis.dto.RmaSimilarResponse.Item;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaSimilarService {

    private final RmaClaimRepository claims;
    private final ObjectProvider<AiClient> aiClients;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RmaSimilarResponse getSimilar(String rmaId, int limit, Role role) {
        RoleAccess.requireMember(role);
        // FastAPI 응답을 기다리는 동안 Spring DB 연결을 점유할 필요가 없음
        claims.requireExists(rmaId);
        AiClient aiClient = aiClients.getIfAvailable();
        if (aiClient == null) {
            throw new AiUnavailableException();
        }
        var result = aiClient.findSimilarCases(rmaId, limit);
        var items = result.items().stream().map(row -> toItem(row, role == Role.QUALITY)).toList();
        return new RmaSimilarResponse(rmaId, KoreaTime.inSeoul(result.asOf()), items);
    }

    private Item toItem(SimilarCase row, boolean quality) {
        // 비교 근거·과거 조치·원문 일부에 내부 설비와 고객 정보가 들어갈 수 있어 QUALITY에만 노출함
        return new Item(row.rmaId(), KoreaTime.inSeoul(row.openedAt()), row.claimDate(), row.minorCode(), row.minorName(),
                row.similarity(), row.daysToResolve(), quality ? row.recurrence() : null,
                quality ? row.basis() : null, quality ? row.pastAction() : null,
                quality ? row.causeCode() : null, quality ? row.causeName() : null, quality ? row.processId() : null,
                quality ? row.equipmentId() : null, quality ? row.certainty() : null, quality ? row.snippet() : null,
                quality ? actions(row) : null);
    }

    private List<Action> actions(SimilarCase row) {
        if (row.correctiveActions() == null) {
            return List.of();
        }
        return row.correctiveActions().stream()
                .map(action -> new Action(action.category(), action.description(), KoreaTime.inSeoul(action.implementedAt())))
                .toList();
    }
}
