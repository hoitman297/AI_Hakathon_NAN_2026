package com.gameproject.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.SabotageEvent;

public interface SabotageEventRepository extends JpaRepository<SabotageEvent, Long> {

    List<SabotageEvent> findBySession(GameSession session);

    List<SabotageEvent> findBySessionAndDay(GameSession session, Integer day);
}
