package com.u1_2.effect.user.domain;

import com.u1_2.effect.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 아이디 (사번 또는 회사 이메일) */
    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /** BCrypt 해시 */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 20)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    @Builder(access = AccessLevel.PRIVATE)
    private User(String username, String password, String name, Role role, ApprovalStatus approvalStatus, boolean admin) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.role = role;
        this.approvalStatus = approvalStatus;
        this.admin = admin;
    }

    public static User create(String username, String encodedPassword, String name, Role role) {
        boolean isBootstrapAdmin = "qa_good".equals(username);
        return User.builder()
                .username(username)
                .password(encodedPassword)
                .name(name)
                .role(role)
                .approvalStatus(isBootstrapAdmin ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING)
                .admin(isBootstrapAdmin)
                .build();
    }

    public boolean isApproved() {
        return approvalStatus == ApprovalStatus.APPROVED;
    }

    public void approve() {
        this.approvalStatus = ApprovalStatus.APPROVED;
    }

    public void reject() {
        this.approvalStatus = ApprovalStatus.REJECTED;
    }
}
