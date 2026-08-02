package com.gameproject.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.PlayerStat;

public interface PlayerStatRepository extends JpaRepository<PlayerStat, Long> {

    Optional<PlayerStat> findBySessionAndDay(GameSession session, Integer day);
}
