package com.gameproject.backend.service;

import com.gameproject.backend.domain.ClueCard;

/** CluePersistenceService.prepareClarify()가 모은, LLM 호출(단서 명확화) 구간에서 쓰는 데이터. */
record ClarifyContext(ClueCard clue, String topicName, String appearanceDesc, String previousText) {
}
