package com.gameproject.llmproxy.dto;

/** backend -> llm-proxy: 오답 고발 후 발생하는 랜덤 이벤트의 연출 텍스트 생성 요청. */
public record EventContentRequest(
        String eventType,
        String target,
        Integer day,
        String accusedNpcName
) {
}
