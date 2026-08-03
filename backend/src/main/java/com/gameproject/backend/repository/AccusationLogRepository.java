package com.gameproject.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.AccusationLog;
import com.gameproject.backend.domain.GameSession;

public interface AccusationLogRepository extends JpaRepository<AccusationLog, Long> {

    List<AccusationLog> findBySession(GameSession session);
}
