package com.gameproject.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.FruitForageState;
import com.gameproject.backend.domain.FruitMaster;
import com.gameproject.backend.domain.GameSession;

public interface FruitForageStateRepository extends JpaRepository<FruitForageState, Long> {

    Optional<FruitForageState> findBySessionAndFruit(GameSession session, FruitMaster fruit);
}
