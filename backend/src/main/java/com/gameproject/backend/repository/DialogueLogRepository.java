package com.gameproject.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.DialogueLog;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;

public interface DialogueLogRepository extends JpaRepository<DialogueLog, Long> {

    List<DialogueLog> findBySessionAndNpcOrderByCreatedAtAsc(GameSession session, Npc npc);
}
