package com.gameproject.backend.service;

import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcLocationSchedule;
import com.gameproject.backend.repository.NpcLocationScheduleRepository;

import lombok.RequiredArgsConstructor;

/**
 * 낮 동선 배치표(💡 제안 단계)의 확률에 따라 "오늘 이 NPC가 어디 있는지"를 결정한다.
 * 같은 (세션, 일차, NPC) 조합이면 항상 같은 위치가 나오도록 시드를 고정해 하루 동안은
 * 위치가 바뀌지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class NpcLocationResolver {

    private final NpcLocationScheduleRepository scheduleRepository;

    public String resolveToday(GameSession session, Npc npc) {
        // DB는 findAll류 조회 순서를 보장하지 않으므로, 시드 기반 확률 계산이
        // 매번 같은 결과를 내도록 명시적으로 정렬한다.
        List<NpcLocationSchedule> schedules = scheduleRepository.findByNpc(npc).stream()
                .sorted(java.util.Comparator.comparing(NpcLocationSchedule::getId))
                .toList();
        if (schedules.isEmpty()) {
            return "알 수 없음";
        }
        long seed = session.getSessionId() * 1_000_003L + session.getCurrentDay() * 97L + npc.getNpcId();
        int roll = new Random(seed).nextInt(100);

        int cumulative = 0;
        for (NpcLocationSchedule schedule : schedules) {
            cumulative += schedule.getProbability();
            if (roll < cumulative) {
                return schedule.getLocationName();
            }
        }
        return schedules.get(0).getLocationName();
    }
}
