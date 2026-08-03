package com.gameproject.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.ClueCard;
import com.gameproject.backend.domain.GameSession;

public interface ClueCardRepository extends JpaRepository<ClueCard, Long> {

    List<ClueCard> findBySession(GameSession session);

    List<ClueCard> findBySessionAndAcquiredTrue(GameSession session);
}
