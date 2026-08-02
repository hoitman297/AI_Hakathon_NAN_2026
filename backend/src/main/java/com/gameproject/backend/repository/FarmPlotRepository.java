package com.gameproject.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.FarmPlot;
import com.gameproject.backend.domain.GameSession;

public interface FarmPlotRepository extends JpaRepository<FarmPlot, Long> {

    List<FarmPlot> findBySession(GameSession session);

    List<FarmPlot> findBySessionAndHarvestedFalse(GameSession session);
}
