package com.gameproject.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcPersonaState;

public interface NpcPersonaStateRepository extends JpaRepository<NpcPersonaState, Long> {

    Optional<NpcPersonaState> findBySessionAndNpc(GameSession session, Npc npc);
}
