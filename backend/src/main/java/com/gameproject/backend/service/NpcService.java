package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.domain.Affinity;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.dto.NpcDetailResponse;
import com.gameproject.backend.dto.NpcSummaryResponse;
import com.gameproject.backend.repository.AffinityRepository;
import com.gameproject.backend.repository.NpcRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NpcService {

    private final NpcRepository npcRepository;
    private final AffinityRepository affinityRepository;
    private final NpcLocationResolver locationResolver;
    private final SessionService sessionService;

    @Transactional(readOnly = true)
    public List<NpcSummaryResponse> listToday(Long sessionId) {
        GameSession session = sessionService.findSession(sessionId);
        return npcRepository.findAll().stream()
                .map(npc -> new NpcSummaryResponse(
                        npc.getNpcId(),
                        npc.getName(),
                        npc.getRole(),
                        locationResolver.resolveToday(session, npc)))
                .toList();
    }

    @Transactional(readOnly = true)
    public NpcDetailResponse getDetail(Long sessionId, Long npcId) {
        GameSession session = sessionService.findSession(sessionId);
        Npc npc = npcRepository.findById(npcId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 NPC입니다: " + npcId));
        int affinity = getAffinityScore(session, npc);

        return new NpcDetailResponse(
                npc.getNpcId(),
                npc.getName(),
                npc.getRole(),
                npc.getAge(),
                npc.getPersonalityDesc(),
                npc.getSpeechStyle(),
                npc.getSampleLine(),
                affinity,
                locationResolver.resolveToday(session, npc)
        );
    }

    /** 현재 호감도 점수 조회 (기록이 없으면 시작값). 대화 LLM 프롬프트에 등급 판단용으로 넘길 때 사용. */
    @Transactional(readOnly = true)
    public int getAffinityScore(GameSession session, Npc npc) {
        return affinityRepository.findBySessionAndNpc(session, npc)
                .map(Affinity::getScore)
                .orElse(GameConstants.AFFINITY_START);
    }

    /** 대화 진행/선물 등으로 인한 호감도 변화를 적용 (0~100 범위로 clamp) */
    @Transactional
    public int adjustAffinity(GameSession session, Npc npc, int delta) {
        Affinity affinity = affinityRepository.findBySessionAndNpc(session, npc)
                .orElseGet(() -> Affinity.builder()
                        .session(session)
                        .npc(npc)
                        .score(GameConstants.AFFINITY_START)
                        .build());
        int newScore = Math.max(0, Math.min(100, affinity.getScore() + delta));
        affinity.setScore(newScore);
        affinity.setUpdatedAt(LocalDateTime.now());
        affinityRepository.save(affinity);
        return newScore;
    }
}
