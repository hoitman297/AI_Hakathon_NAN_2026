package com.gameproject.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.NpcCaseAssignment;

public interface NpcCaseAssignmentRepository extends JpaRepository<NpcCaseAssignment, Long> {

    Optional<NpcCaseAssignment> findBySession(GameSession session);
}
