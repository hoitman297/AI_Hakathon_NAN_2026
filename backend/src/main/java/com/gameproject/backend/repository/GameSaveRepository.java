package com.gameproject.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.GameSave;

public interface GameSaveRepository extends JpaRepository<GameSave, Long> {
}
