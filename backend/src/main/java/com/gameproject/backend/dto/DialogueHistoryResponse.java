package com.gameproject.backend.dto;

import java.util.List;

/** 대화창을 열 때 조회 — 지금까지의 대화 기록과 함께, 대화창을 새로 열어도 초기화되지 않는
 *  "오늘 이 NPC와 나눈 대화 횟수"를 같이 내려줘서 프론트가 처음 열자마자 남은 횟수를 정확히 안다. */
public record DialogueHistoryResponse(
        List<DialogueMessageResponse> messages,
        int exchangesUsedToday,
        int maxExchangesPerDay
) {
}
