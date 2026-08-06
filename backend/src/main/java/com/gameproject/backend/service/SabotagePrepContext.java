package com.gameproject.backend.service;

import com.gameproject.backend.domain.ClueTopic;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.SabotageType;

/**
 * SessionPersistenceService.prepareAdvanceDay()가 모은, 그날 밤 사보타주 연출/단서
 * LLM 호출(트랜잭션 밖)에서 쓰는 데이터. witness는 npcRepository로 직접 조회한(지연 로딩
 * 프록시가 아닌) 완전히 로드된 엔티티라 트랜잭션 밖에서도 안전하다.
 */
record SabotagePrepContext(
        String location,
        SabotageType type,
        String subTarget,
        boolean selfDecoy,
        Npc witness,
        String witnessHint,
        ClueTopic topic,
        String culpritAppearanceDesc,
        String culpritPersonalityDesc) {
}
