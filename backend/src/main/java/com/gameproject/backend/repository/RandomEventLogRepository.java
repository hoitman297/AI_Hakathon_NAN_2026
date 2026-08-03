package com.gameproject.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.RandomEventLog;

public interface RandomEventLogRepository extends JpaRepository<RandomEventLog, Long> {

    List<RandomEventLog> findBySession(GameSession session);
}
