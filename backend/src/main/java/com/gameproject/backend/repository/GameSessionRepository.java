package com.gameproject.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.Account;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.SessionStatus;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    Optional<GameSession> findFirstByAccountAndStatusOrderByStartedAtDesc(Account account, SessionStatus status);

    /** 세이브 목록 화면용 — 삭제된 세션은 excludedStatus(DELETED)로 걸러서 뺀다. */
    List<GameSession> findByAccountAndStatusNotOrderByStartedAtDesc(Account account, SessionStatus excludedStatus);

    /** 세이브 슬롯(계정당 최대 3개) 여유 확인용 — 삭제된 세션은 슬롯을 차지하지 않는다. */
    long countByAccountAndStatusNot(Account account, SessionStatus excludedStatus);
}
