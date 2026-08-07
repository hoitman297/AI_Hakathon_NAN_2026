package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcWitnessAwareness;
import com.gameproject.backend.domain.SabotageEvent;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.NpcWitnessAwarenessRepository;
import com.gameproject.backend.repository.SabotageEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * 목격담을 원래 목격자 너머로 퍼뜨리는 서비스. 기획서상 명확히 확인되는 관계
 * (AccusationPersistenceService.CLOSE_PARTNER와 동일한 나주부-전주인 부부)와
 * "마을 소식통" 캐릭터 설정(박영계)만 근거로 삼고, 기획서에 없는 관계는 새로 만들지 않는다.
 *
 * <p>새 문장을 LLM으로 생성하지 않고 SabotageEvent에 이미 있는 day/location만 재사용한다 —
 * 목격담 프롬프트 자체가 "무엇을 봤는지는 몰라야 한다"는 원칙으로 설계돼 있어서(범인 특정
 * 정보가 아예 없음), 이 서비스가 하는 일은 "누가 그 소문을 아는지"를 관계 그래프 1단계씩만
 * (호출 시점당 1홉) 넓히는 것뿐이다 — 범인 노출 위험도, 추가 LLM 호출도 없다.
 */
@Service
@RequiredArgsConstructor
class WitnessGossipService {

    private static final Map<String, String> CLOSE_PARTNER = Map.of(
            "나주부", "전주인",
            "전주인", "나주부"
    );

    private static final String GOSSIP_HUB_NAME = "박영계";

    private final SabotageEventRepository sabotageEventRepository;
    private final NpcWitnessAwarenessRepository awarenessRepository;
    private final NpcRepository npcRepository;

    private final Random random = new Random();

    /** night→day 전환마다 호출. 호출부(SessionPersistenceService.finalizeAdvanceDay)가 이미
     * 열어둔 트랜잭션 안에서 실행되므로 여기서 별도로 @Transactional을 걸지 않는다. */
    void spreadOvernight(GameSession session, int newDay) {
        List<SabotageEvent> witnessedEvents = sabotageEventRepository.findBySession(session).stream()
                .filter(event -> event.getWitnessNpc() != null)
                .toList();
        if (witnessedEvents.isEmpty()) {
            return;
        }

        List<Npc> allNpcs = npcRepository.findAll();

        for (SabotageEvent event : witnessedEvents) {
            Set<String> knowerNames = new HashSet<>();
            knowerNames.add(event.getWitnessNpc().getName());
            awarenessRepository.findBySessionAndSabotageEvent(session, event)
                    .forEach(a -> knowerNames.add(a.getNpc().getName()));

            // 이번 밤에는 "지금 시점까지 알고 있던" 사람들 기준으로만 1홉 전파한다 — 같은 밤에
            // 새로 알게 된 사람이 그 밤 안에 또 다음 사람에게 전하는 연쇄는 만들지 않는다.
            for (String knowerName : List.copyOf(knowerNames)) {
                for (String candidateName : relatedNames(knowerName, allNpcs)) {
                    if (knowerNames.contains(candidateName)) {
                        continue;
                    }
                    int chancePercent = isClosePair(knowerName, candidateName)
                            ? GameConstants.WITNESS_SPREAD_CLOSE_CHANCE_PERCENT
                            : GameConstants.WITNESS_SPREAD_HUB_CHANCE_PERCENT;
                    if (random.nextInt(100) >= chancePercent) {
                        continue;
                    }
                    Npc candidate = findByName(allNpcs, candidateName);
                    if (candidate == null) {
                        continue;
                    }
                    awarenessRepository.save(NpcWitnessAwareness.builder()
                            .session(session).sabotageEvent(event).npc(candidate)
                            .learnedDay(newDay).createdAt(LocalDateTime.now())
                            .build());
                    knowerNames.add(candidateName);
                }
            }
        }
    }

    private boolean isClosePair(String a, String b) {
        return b.equals(CLOSE_PARTNER.get(a));
    }

    /** name과 관계로 연결된(정보를 주고받을 법한) 다른 NPC 이름 목록. */
    private List<String> relatedNames(String name, List<Npc> allNpcs) {
        List<String> related = new ArrayList<>();
        String partner = CLOSE_PARTNER.get(name);
        if (partner != null) {
            related.add(partner);
        }
        if (name.equals(GOSSIP_HUB_NAME)) {
            allNpcs.stream().map(Npc::getName).filter(n -> !n.equals(GOSSIP_HUB_NAME)).forEach(related::add);
        } else {
            related.add(GOSSIP_HUB_NAME);
        }
        return related;
    }

    private Npc findByName(List<Npc> allNpcs, String name) {
        return allNpcs.stream().filter(n -> name.equals(n.getName())).findFirst().orElse(null);
    }
}
