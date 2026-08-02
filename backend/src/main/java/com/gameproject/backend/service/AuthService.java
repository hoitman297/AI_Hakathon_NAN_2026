package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.domain.Account;
import com.gameproject.backend.dto.AccountResponse;
import com.gameproject.backend.dto.AuthResponse;
import com.gameproject.backend.dto.LoginRequest;
import com.gameproject.backend.dto.SignupRequest;
import com.gameproject.backend.repository.AccountRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** 로그인 토큰 유효 기간. */
    private static final long TOKEN_TTL_HOURS = 24 * 7;

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (accountRepository.existsByUsername(request.username())) {
            throw new IllegalStateException("이미 사용 중인 아이디입니다: " + request.username());
        }

        Account account = accountRepository.save(Account.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .createdAt(LocalDateTime.now())
                .build());

        return issueToken(account);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Account account = accountRepository.findByUsername(request.username())
                .orElseThrow(() -> new AuthException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new AuthException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        account.setLastLoginAt(LocalDateTime.now());
        return issueToken(account);
    }

    @Transactional
    public void logout(String token) {
        accountRepository.findByAuthToken(token).ifPresent(account -> {
            account.setAuthToken(null);
            account.setTokenExpiresAt(null);
        });
    }

    @Transactional(readOnly = true)
    public AccountResponse me(String token) {
        Account account = requireValidToken(token);
        return new AccountResponse(account.getAccountId(), account.getUsername(), account.getNickname());
    }

    /** 다른 서비스(예: 세션 생성)에서 Bearer 토큰으로 로그인 계정을 식별할 때 사용. */
    @Transactional(readOnly = true)
    public Account requireValidToken(String token) {
        Account account = accountRepository.findByAuthToken(token)
                .orElseThrow(() -> new AuthException("유효하지 않은 토큰입니다."));
        if (account.getTokenExpiresAt() == null || account.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthException("토큰이 만료되었습니다. 다시 로그인해주세요.");
        }
        return account;
    }

    private AuthResponse issueToken(Account account) {
        String token = UUID.randomUUID().toString();
        account.setAuthToken(token);
        account.setTokenExpiresAt(LocalDateTime.now().plusHours(TOKEN_TTL_HOURS));
        accountRepository.save(account);
        return new AuthResponse(account.getAccountId(), account.getUsername(), account.getNickname(), token);
    }
}
