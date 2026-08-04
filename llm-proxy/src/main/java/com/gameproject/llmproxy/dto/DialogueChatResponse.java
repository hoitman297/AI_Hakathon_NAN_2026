package com.gameproject.llmproxy.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 대화용 LLM의 구조화 출력 스키마. 캐릭터 대사(reply)뿐 아니라, 이번 플레이어 발화가
 * 이 NPC의 호감도에 어떤 영향을 줬는지도 LLM이 함께 판단해서 반환한다.
 */
public record DialogueChatResponse(
        @JsonPropertyDescription("이 캐릭터의 말투와 배경에 맞춘 1~3문장 분량의 대사")
        String reply,
        @JsonPropertyDescription(
                "이 캐릭터의 성격/가치관에 비추어 방금 플레이어 발화가 호감도에 준 영향을 -5(강한 반감)"
                        + "~+5(강한 호감) 사이 정수로 평가. 우호적이거나 그 캐릭터가 좋아할 만한 태도면 양수, "
                        + "무례하거나 그 캐릭터가 싫어할 만한 태도면 음수, 특별한 영향이 없으면 0")
        Integer affinityDelta
) {
}
