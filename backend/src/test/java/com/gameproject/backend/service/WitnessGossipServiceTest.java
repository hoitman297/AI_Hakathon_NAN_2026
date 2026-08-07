package com.gameproject.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gameproject.backend.domain.Account;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcWitnessAwareness;
import com.gameproject.backend.domain.SabotageEvent;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.NpcWitnessAwarenessRepository;
import com.gameproject.backend.repository.SabotageEventRepository;

/**
 * 확률(random.nextInt)에 실제로 의존하는 전파 성공/실패 갈림길은 AccusationPersistenceService의
 * "가까운 관계" 페널티와 마찬가지로 이 프로젝트에서 Random을 주입하지 않는 관례를 따르므로
 * 여기서 검증하지 않는다 — 대신 확률과 무관하게 항상 참이어야 하는 가드 조건(목격자 없음,
 * 이미 전원이 아는 상태)만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class WitnessGossipServiceTest {

    @Mock
    private SabotageEventRepository sabotageEventRepository;
    @Mock
    private NpcWitnessAwarenessRepository awarenessRepository;
    @Mock
    private NpcRepository npcRepository;

    private WitnessGossipService service;

    private GameSession session;
    private Npc najubu;
    private Npc jeonjuin;
    private Npc parkYounggye;
    private Npc hyeonSudong;

    @BeforeEach
    void setUp() {
        service = new WitnessGossipService(sabotageEventRepository, awarenessRepository, npcRepository);

        Account account = Account.builder().accountId(1L).username("u").passwordHash("h").nickname("n")
                .createdAt(LocalDateTime.now()).build();
        najubu = Npc.builder().npcId(2L).name("나주부").role("아내").build();
        session = GameSession.builder()
                .sessionId(100L).account(account).culprit(najubu)
                .currentDay(2).status(SessionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .sneakersEquipped(false)
                .build();
        jeonjuin = Npc.builder().npcId(3L).name("전주인").role("남편, 상점 주인").build();
        parkYounggye = Npc.builder().npcId(4L).name("박영계").role("양계장 주인").build();
        hyeonSudong = Npc.builder().npcId(1L).name("현수동").role("이장").build();
    }

    @Test
    void spreadOvernight_noWitnessedEvents_doesNothingAndSkipsNpcLookup() {
        SabotageEvent noWitness = SabotageEvent.builder().session(session).day(1).location("수박밭")
                .createdAt(LocalDateTime.now()).build();
        when(sabotageEventRepository.findBySession(session)).thenReturn(List.of(noWitness));

        service.spreadOvernight(session, 2);

        verify(npcRepository, never()).findAll();
        verify(awarenessRepository, never()).save(any());
    }

    @Test
    void spreadOvernight_eventAlreadyKnownByEveryoneRelated_savesNothingNew() {
        // 목격자는 나주부. 관계 그래프상 도달 가능한 모든 사람(부부 상대 전주인, 마을 소식통
        // 박영계, 그리고 박영계와 연결된 나머지 현수동)이 이미 다 아는 상태라면, 확률과
        // 무관하게(이미 아는 사람에게는 애초에 확률을 굴리지 않으므로) 더 퍼질 곳이 없어야 한다.
        SabotageEvent event = SabotageEvent.builder().session(session).day(1).location("양계장")
                .witnessNpc(najubu).createdAt(LocalDateTime.now()).build();
        when(sabotageEventRepository.findBySession(session)).thenReturn(List.of(event));
        when(npcRepository.findAll()).thenReturn(List.of(hyeonSudong, najubu, jeonjuin, parkYounggye));

        List<Npc> alreadyKnow = List.of(jeonjuin, parkYounggye, hyeonSudong);
        when(awarenessRepository.findBySessionAndSabotageEvent(session, event))
                .thenReturn(alreadyKnow.stream()
                        .map(n -> NpcWitnessAwareness.builder()
                                .session(session).sabotageEvent(event).npc(n).learnedDay(1)
                                .createdAt(LocalDateTime.now()).build())
                        .toList());

        service.spreadOvernight(session, 2);

        verify(awarenessRepository, never()).save(any());
    }
}
