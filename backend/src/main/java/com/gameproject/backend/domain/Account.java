package com.gameproject.backend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

/** 로그인 계정. GameSession.account로 연동한다. */
@Entity
@Table(name = "account", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
@Comment("로그인 계정")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @Comment("로그인 아이디 (유일)")
    @Column(nullable = false, length = 50)
    private String username;

    @Comment("BCrypt로 해시된 비밀번호")
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Comment("게임 내 표시 닉네임")
    @Column(nullable = false, length = 50)
    private String nickname;

    @Comment("현재 유효한 로그인 토큰 (opaque, 로그아웃 시 null로 초기화)")
    @Column(name = "auth_token", length = 36)
    private String authToken;

    @Comment("auth_token 만료 시각")
    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
