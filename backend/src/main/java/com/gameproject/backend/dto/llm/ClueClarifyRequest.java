package com.gameproject.backend.dto.llm;

/** backend -> llm-proxy: 돋보기 사용 시 단서 문구를 더 구체적으로 갱신하는 요청. */
public record ClueClarifyRequest(
        String topic,
        String npcAppearanceDesc,
        String previousText
) {
}
