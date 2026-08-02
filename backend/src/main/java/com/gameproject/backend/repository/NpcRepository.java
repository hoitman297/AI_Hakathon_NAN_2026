package com.gameproject.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.Npc;

public interface NpcRepository extends JpaRepository<Npc, Long> {
}
