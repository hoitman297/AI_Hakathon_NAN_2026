package com.gameproject.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcLocationSchedule;

public interface NpcLocationScheduleRepository extends JpaRepository<NpcLocationSchedule, Long> {

    List<NpcLocationSchedule> findByNpc(Npc npc);
}
