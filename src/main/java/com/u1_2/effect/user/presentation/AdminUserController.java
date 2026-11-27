package com.u1_2.effect.user.presentation;

import com.u1_2.effect.config.jwt.AuthUser;
import com.u1_2.effect.global.annotation.CurrentUser;
import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.user.application.AuthService;
import com.u1_2.effect.user.domain.ApprovalStatus;
import com.u1_2.effect.user.domain.Role;
import com.u1_2.effect.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Users", description = "관리자(qa_good) 회원 가입 승인·반려")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AuthService authService;

    @Operation(summary = "회원 목록 조회 (status 미지정 시 전체)")
    @GetMapping
    public ResponseEntity<ResponseDto<AdminUserListResponse>> list(
            @RequestParam(required = false) ApprovalStatus status,
            @CurrentUser AuthUser authUser
    ) {
        List<AdminUserItem> items = authService.listUsersForAdmin(authUser.id(), status).stream()
                .map(AdminUserItem::from)
                .toList();
        return ResponseEntity.ok(ResponseDto.ofSuccess(new AdminUserListResponse(items)));
    }

    @Operation(summary = "회원 가입 승인")
    @PostMapping("/{userId}/approve")
    public ResponseEntity<ResponseDto<AdminUserItem>> approve(
            @PathVariable Long userId,
            @CurrentUser AuthUser authUser
    ) {
        User user = authService.approveUser(authUser.id(), userId);
        return ResponseEntity.ok(ResponseDto.ofSuccess(AdminUserItem.from(user)));
    }

    @Operation(summary = "회원 가입 반려")
    @PostMapping("/{userId}/reject")
    public ResponseEntity<ResponseDto<AdminUserItem>> reject(
            @PathVariable Long userId,
            @CurrentUser AuthUser authUser
    ) {
        User user = authService.rejectUser(authUser.id(), userId);
        return ResponseEntity.ok(ResponseDto.ofSuccess(AdminUserItem.from(user)));
    }

    public record AdminUserListResponse(List<AdminUserItem> items) {
    }

    public record AdminUserItem(
            Long id,
            String username,
            String name,
            Role role,
            String roleLabel,
            ApprovalStatus approvalStatus,
            boolean isAdmin,
            LocalDateTime createdAt
    ) {
        static AdminUserItem from(User user) {
            return new AdminUserItem(
                    user.getId(),
                    user.getUsername(),
                    user.getName(),
                    user.getRole(),
                    user.getRole().getLabel(),
                    user.getApprovalStatus(),
                    user.isAdmin(),
                    user.getCreatedAt()
            );
        }
    }
}
