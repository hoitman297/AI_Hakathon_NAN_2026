package com.gameproject.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.GameSession;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
}
