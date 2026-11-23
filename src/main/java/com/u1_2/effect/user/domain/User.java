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

    @Builder(access = AccessLevel.PRIVATE)
    private User(String username, String password, String name, Role role) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.role = role;
    }

    public static User create(String username, String encodedPassword, String name, Role role) {
        return User.builder()
                .username(username)
                .password(encodedPassword)
                .name(name)
                .role(role)
                .build();
    }
}
