package com.gameproject.llmproxy.dto;

/** backend -> llm-proxy: 밤 사보타주 다음날 아침 알림용 연출 텍스트 생성 요청. */
public record SabotageSummaryRequest(
        String location,
        String type,
        String subTarget,
        Integer day
) {
}
