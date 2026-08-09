package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.client.LlmProxyClient;
import com.gameproject.backend.domain.ClueCard;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.dto.ClueCardResponse;
import com.gameproject.backend.dto.UnacquiredClueResponse;
import com.gameproject.backend.repository.ClueCardRepository;

import lombok.RequiredArgsConstructor;

/**
 * 단서 습득/조회와 명확화(clarify) 흐름 담당. clarify()의 DB 읽기/쓰기는
 * {@link CluePersistenceService}의 짧은 트랜잭션들로 처리하고, 이 클래스는 그 사이에서
 * LLM(llm-proxy) 호출만 담당한다 — 이유는 DialogueChatPersistenceService 클래스 주석 참고.
 */
@Service
@RequiredArgsConstructor
public class ClueService {

    private final ClueCardRepository clueCardRepository;
    private final SessionService sessionService;
    private final LlmProxyClient llmProxyClient;
    private final CluePersistenceService persistence;

    @Transactional(readOnly = true)
    public List<ClueCardResponse> listAcquired(Long sessionId) {
        GameSession session = sessionService.findSession(sessionId);
        return clueCardRepository.findBySessionAndAcquiredTrue(session).stream()
                .map(ClueService::toResponse)
                .toList();
    }

    /**
     * 아직 습득하지 않은 단서의 발생 장소 목록 — 지도 위 어디로 가서 습득(acquire)해야 하는지
     * 프론트가 알 방법이 없어서 신설. 범인 정보(NpcCaseAssignment)는 전혀 조회하지 않는다.
     */
    @Transactional(readOnly = true)
    public List<UnacquiredClueResponse> listUnacquired(Long sessionId) {
        GameSession session = sessionService.findSession(sessionId);
        return clueCardRepository.findBySession(session).stream()
                .filter(clue -> !Boolean.TRUE.equals(clue.getAcquired()))
                .map(clue -> new UnacquiredClueResponse(
                        clue.getClueId(),
                        clue.getSabotageEvent().getLocation(),
                        clue.getSabotageEvent().getDay(),
                        clue.getTopic().name()))
                .toList();
    }

    /** 사보타주 발생 장소에 가서 습득하는 동작 (지도/이동은 클라이언트 담당, 여기선 상태만 기록) */
    @Transactional
    public ClueCardResponse acquire(Long sessionId, Long clueId) {
        GameSession session = sessionService.findSession(sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("이미 종료된 세션입니다.");
        }
        ClueCard clue = getOwnedClue(session, clueId);
        if (Boolean.TRUE.equals(clue.getAcquired())) {
            return toResponse(clue);
        }
        clue.setAcquired(true);
        clue.setAcquiredAt(LocalDateTime.now());
        return toResponse(clueCardRepository.save(clue));
    }

    /** 돋보기 1개를 소모해서 단서 문구를 더 명확하게 갱신 (1일 1회, 이미 명확화된 단서는 재사용 불가) */
    public ClueCardResponse clarify(Long sessionId, Long clueId) {
        ClarifyContext ctx = persistence.prepareClarify(sessionId, clueId);
        String clarifiedText = llmProxyClient.clarifyClueContent(ctx.topicName(), ctx.appearanceDesc(), ctx.previousText());
        return persistence.saveClarifiedText(ctx.clue(), clarifiedText);
    }

    private ClueCard getOwnedClue(GameSession session, Long clueId) {
        ClueCard clue = clueCardRepository.findById(clueId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 단서입니다: " + clueId));
        if (!clue.getSession().getSessionId().equals(session.getSessionId())) {
            throw new IllegalArgumentException("이 세션의 단서가 아닙니다.");
        }
        return clue;
    }

    static ClueCardResponse toResponse(ClueCard clue) {
        String text = clue.getTextClarified() != null ? clue.getTextClarified() : clue.getTextAmbiguous();
        return new ClueCardResponse(clue.getClueId(), clue.getTopic().name(), text, clue.getTextClarified() != null);
    }
}
