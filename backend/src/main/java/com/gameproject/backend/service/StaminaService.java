package com.gameproject.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.repository.PlayerStatRepository;

import lombok.RequiredArgsConstructor;

/** 낮 행동(이동/대화/파종/수확/채집)에 공통으로 쓰이는 체력 차감 로직. */
@Service
@RequiredArgsConstructor
public class StaminaService {

    private final PlayerStatRepository playerStatRepository;

    @Transactional
    public PlayerStat consume(GameSession session, double amount) {
        PlayerStat stat = playerStatRepository.findBySessionAndDay(session, session.getCurrentDay())
                .orElseThrow(() -> new IllegalStateException("현재 일차의 플레이어 상태가 없습니다."));

        if (Boolean.TRUE.equals(stat.getFainted())) {
            throw new IllegalStateException("이미 기절 상태입니다. 오늘은 더 행동할 수 없습니다.");
        }

        double newStamina = stat.getStaminaCurrent() - amount;
        if (newStamina <= 0) {
            newStamina = 0;
            stat.setFainted(true);
        }
        stat.setStaminaCurrent(newStamina);
        return playerStatRepository.save(stat);
    }

    @Transactional
    public PlayerStat restore(GameSession session, double amount) {
        PlayerStat stat = currentStat(session);
        stat.setStaminaCurrent(Math.min(stat.getStaminaMax(), stat.getStaminaCurrent() + amount));
        return playerStatRepository.save(stat);
    }

    @Transactional(readOnly = true)
    public PlayerStat currentStat(GameSession session) {
        return playerStatRepository.findBySessionAndDay(session, session.getCurrentDay())
                .orElseThrow(() -> new IllegalStateException("현재 일차의 플레이어 상태가 없습니다."));
    }

    @Transactional
    public void addGold(GameSession session, int delta) {
        PlayerStat stat = currentStat(session);
        stat.setGold(stat.getGold() + delta);
        playerStatRepository.save(stat);
    }
}
