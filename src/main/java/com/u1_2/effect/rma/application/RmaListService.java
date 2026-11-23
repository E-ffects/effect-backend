package com.u1_2.effect.rma.application;

import com.u1_2.effect.global.dto.PageParameters;
import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;
import com.u1_2.effect.rma.domain.RmaListRepository;
import com.u1_2.effect.rma.domain.RmaListRepository.Claim;
import com.u1_2.effect.rma.domain.RmaListRepository.Filter;
import com.u1_2.effect.rma.domain.RmaListRepository.Sort;
import com.u1_2.effect.rma.domain.RmaListRepository.SortColumn;
import com.u1_2.effect.rma.presentation.dto.GetRmaListRequest;
import com.u1_2.effect.rma.presentation.dto.RmaListResponse;
import com.u1_2.effect.rma.presentation.dto.RmaListResponse.Item;
import com.u1_2.effect.rma.presentation.dto.SearchRmaRequest;
import com.u1_2.effect.user.application.RoleAccess;
import com.u1_2.effect.user.domain.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaListService {

    private final RmaListRepository repository;
    private final Clock appClock;

    public RmaListResponse getList(SearchRmaRequest request, GetRmaListRequest pagination, Role role) {
        Search search = validateSearch(request, role);
        var page = PageParameters.resolve(pagination.page(), pagination.size());
        var result = repository.search(search.filter(), LocalDate.now(appClock), search.sort(),
                page.page(), page.size(), pagination.open());

        return new RmaListResponse(toItems(result.items(), role), result.total(), result.page(),
                page.size(), result.openFound(), result.openExists());
    }

    public List<Item> getExportRows(SearchRmaRequest request, Role role) {
        Search search = validateSearch(request, role);
        return toItems(repository.findAll(search.filter(), LocalDate.now(appClock), search.sort()), role);
    }

    private Search validateSearch(SearchRmaRequest request, Role role) {
        RoleAccess.requireMember(role);
        if (request.from() != null && request.to() != null && request.from().isAfter(request.to())) {
            throw invalidInput();
        }

        List<Sort> sort = parseSort(request.sort());
        if (role != Role.QUALITY && (request.severity() != null || request.recurrence() != null
                || sort.stream().anyMatch(order -> order.column() == SortColumn.SEVERITY))) {
            throw invalidInput();
        }
        var filter = new Filter(request.q(), request.product(), request.defectType(), request.classification(),
                request.status(), request.severity(), request.recurrence(), request.from(), request.to(),
                request.overDays());
        return new Search(filter, sort);
    }

    private List<Item> toItems(List<Claim> claims, Role role) {
        // 목록과 CSV에 같은 역할별 필드 제한을 적용함
        boolean quality = role == Role.QUALITY;
        return claims.stream().map(row -> new Item(
                row.rmaId(), row.openedAt(), row.customerName(), row.reportedPartNo(), row.detectionStage(),
                row.majorName(), row.minorName(), row.classificationStatus(), row.claimedDefectQty(),
                quality ? row.severity() : null, row.analysisLevel(), row.csStatus(), quality ? row.qaStatus() : null,
                row.intakeState(), row.analysisState(), quality ? row.approvalStatus() : null,
                quality ? row.recurrenceVerdict() : null
        )).toList();
    }

    private List<Sort> parseSort(String value) {
        if (value == null || value.isBlank()) {
            return List.of(new Sort(SortColumn.OPENED_AT, true));
        }
        // 정렬명과 방향을 허용 목록으로 제한해 요청 값을 SQL에 직접 넣지 않음
        return Arrays.stream(value.split(",", -1)).map(token -> {
            String[] parts = token.split(":", -1);
            if (parts.length > 2 || (parts.length == 2 && !List.of("asc", "desc").contains(parts[1]))) {
                throw invalidInput();
            }
            SortColumn column = Arrays.stream(SortColumn.values())
                    .filter(field -> field.apiName().equals(parts[0]))
                    .findFirst().orElseThrow(RmaListService::invalidInput);
            return new Sort(column, parts.length == 1 || "desc".equals(parts[1]));
        }).distinct().toList();
    }

    private static ApiException invalidInput() {
        return new ApiException(CommonErrorCode.INVALID_INPUT);
    }

    private record Search(Filter filter, List<Sort> sort) {
    }
}
