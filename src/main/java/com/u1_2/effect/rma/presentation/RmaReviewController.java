package com.u1_2.effect.rma.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.PageResponse;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.rma.application.review.RmaReviewService;
import com.u1_2.effect.rma.domain.review.RmaReviewRepository.ApprovalLevel;
import com.u1_2.effect.rma.domain.review.RmaReviewRepository.ApprovalStatus;
import com.u1_2.effect.rma.presentation.dto.RmaApprovalResponse;
import com.u1_2.effect.rma.presentation.dto.RmaTriageReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RmaReviewController {

    private final RmaReviewService service;

    @Operation(summary = "등급과 상태별 원인 판정 목록 조회")
    @GetMapping("/approvals")
    public ResponseEntity<ResponseDto<PageResponse<RmaApprovalResponse>>> getApprovals(
            @RequestParam(defaultValue = "WAITING") ApprovalStatus status,
            @RequestParam(defaultValue = "L3") ApprovalLevel level,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.getApprovals(user.role(), status, level, page, size)));
    }

    @Operation(summary = "품질관리자 확인이 필요한 임시 QA 판정 조회")
    @GetMapping("/triage-reviews")
    public ResponseEntity<ResponseDto<PageResponse<RmaTriageReviewResponse>>> getTriageReviews(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(ResponseDto.ofSuccess(service.getTriageReviews(user.role(), page, size)));
    }
}
