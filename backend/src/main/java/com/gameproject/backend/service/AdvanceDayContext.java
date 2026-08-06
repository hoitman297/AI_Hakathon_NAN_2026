package com.gameproject.backend.service;

import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.PlayerStat;

/**
 * SessionPersistenceService.prepareAdvanceDay()의 결과. sabotage가 non-null이면(1~5일차)
 * 그날 밤 사보타주 연출/단서 LLM 호출이 필요하다는 뜻이고, null이면(6일차 이후) 곧바로
 * finalizeAdvanceDay()로 넘어가면 된다.
 */
record AdvanceDayContext(GameSession session, PlayerStat today, int day, SabotagePrepContext sabotage) {
}
