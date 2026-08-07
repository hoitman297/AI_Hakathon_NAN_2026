package com.gameproject.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcWitnessAwareness;
import com.gameproject.backend.domain.SabotageEvent;

public interface NpcWitnessAwarenessRepository extends JpaRepository<NpcWitnessAwareness, Long> {

    List<NpcWitnessAwareness> findBySessionAndSabotageEvent(GameSession session, SabotageEvent sabotageEvent);

    List<NpcWitnessAwareness> findBySessionAndNpc(GameSession session, Npc npc);

    boolean existsBySabotageEventAndNpc(SabotageEvent sabotageEvent, Npc npc);
}
