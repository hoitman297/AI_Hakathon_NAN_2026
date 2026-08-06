package com.gameproject.backend.service;

import com.gameproject.backend.domain.EventTarget;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;

/**
 * AccusationPersistenceService.prepareAccusation()이 모든 DB 쓰기(고발 로그, 호감도 페널티,
 * 세션 상태 전이)를 먼저 끝낸 뒤 반환하는 결과. correct==true거나 이벤트 묘사가 필요 없으면
 * 이 시점 이후로는 LLM 호출과 무관하게 최종 상태가 이미 DB에 커밋되어 있다 — accused/session은
 * npcRepository.findById()/sessionRepository.findById()로 직접 조회한 완전히 로드된 엔티티라
 * (지연 로딩 프록시 아님) 트랜잭션이 끝난 뒤에도 필드 접근이 안전하다.
 */
record AccusationOutcome(
        boolean correct,
        String sessionStatusName,
        Npc accused,
        boolean needsEventDescription,
        String eventType,
        EventTarget eventTarget,
        int day,
        GameSession session) {
}
