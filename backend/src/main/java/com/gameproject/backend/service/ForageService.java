package com.gameproject.backend.service;

import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.domain.FruitForageState;
import com.gameproject.backend.domain.FruitMaster;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.InventoryItemType;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.dto.AvailableFruitResponse;
import com.gameproject.backend.dto.ForageResponse;
import com.gameproject.backend.repository.FruitForageStateRepository;
import com.gameproject.backend.repository.FruitMasterRepository;

import lombok.RequiredArgsConstructor;

/**
 * 채집 시스템. 기획서 원안에는 "그날 익어있는 과일 종류(1~2개)" 개념만 있고 구현
 * 세부 테이블이 없어서, 세션+일차 기준 결정론적 랜덤으로 오늘의 과일 풀을 계산하고
 * (session_id, fruit_id) 단위로 재생 쿨다운을 근사하는 방식으로 구현했다.
 * "같은 나무" 단위가 아니라 "같은 과일 종류" 단위로 쿨다운을 근사한 점은 원안과 다름.
 */
@Service
@RequiredArgsConstructor
public class ForageService {

    private final FruitMasterRepository fruitMasterRepository;
    private final FruitForageStateRepository forageStateRepository;
    private final SessionService sessionService;
    private final StaminaService staminaService;
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public List<AvailableFruitResponse> listAvailableToday(Long sessionId) {
        GameSession session = sessionService.findSession(sessionId);
        List<FruitMaster> eligible = eligibleFruits(session);

        long seed = session.getSessionId() * 1_000_003L + session.getCurrentDay() * 31L;
        Random random = new Random(seed);
        int count = Math.min(eligible.size(), 1 + random.nextInt(2)); // 1~2종

        List<FruitMaster> shuffled = new java.util.ArrayList<>(eligible);
        java.util.Collections.shuffle(shuffled, random);

        return shuffled.stream()
                .limit(count)
                .map(f -> new AvailableFruitResponse(f.getFruitId(), f.getName()))
                .toList();
    }

    @Transactional
    public ForageResponse forage(Long sessionId, Long fruitId) {
        GameSession session = sessionService.findSession(sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("이미 종료된 세션입니다.");
        }
        FruitMaster fruit = fruitMasterRepository.findById(fruitId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 과일입니다: " + fruitId));

        boolean isAvailable = listAvailableToday(sessionId).stream()
                .anyMatch(f -> f.fruitId().equals(fruitId));
        if (!isAvailable) {
            throw new IllegalStateException("오늘은 채집할 수 없는 과일입니다 (재생 주기 중이거나 오늘 등장하지 않음).");
        }

        var stat = staminaService.consume(session, fruit.getForageStamina());

        FruitForageState state = forageStateRepository.findBySessionAndFruit(session, fruit)
                .orElseGet(() -> FruitForageState.builder().session(session).fruit(fruit).build());
        state.setLastForagedDay(session.getCurrentDay());
        forageStateRepository.save(state);

        inventoryService.addItem(session, InventoryItemType.FRUIT, fruit.getFruitId(), 1);

        return new ForageResponse(fruit.getFruitId(), fruit.getName(), stat.getStaminaCurrent());
    }

    private List<FruitMaster> eligibleFruits(GameSession session) {
        return fruitMasterRepository.findAll().stream()
                .filter(fruit -> {
                    FruitForageState state = forageStateRepository.findBySessionAndFruit(session, fruit).orElse(null);
                    if (state == null || state.getLastForagedDay() == null) {
                        return true;
                    }
                    if (state.getLastForagedDay().equals(session.getCurrentDay())) {
                        return true; // 오늘 이미 채집한 과일은 오늘 안에서는 계속 채집 가능
                    }
                    return (session.getCurrentDay() - state.getLastForagedDay()) >= fruit.getRegenDays();
                })
                .sorted(java.util.Comparator.comparing(FruitMaster::getFruitId))
                .toList();
    }
}
