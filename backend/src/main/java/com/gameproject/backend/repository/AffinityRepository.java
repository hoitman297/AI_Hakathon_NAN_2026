package com.gameproject.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.Affinity;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;

public interface AffinityRepository extends JpaRepository<Affinity, Long> {

    Optional<Affinity> findBySessionAndNpc(GameSession session, Npc npc);

    List<Affinity> findBySession(GameSession session);
}
