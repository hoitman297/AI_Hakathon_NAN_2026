package com.gameproject.llmproxy.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 페르소나 생성 LLM의 구조화 출력 스키마. 대화용 LLM이 이 JSON을 그대로 시스템 프롬프트
 * 재료로 쓰므로, 대화에 필요한 정보(말투/배경/동기)를 자체 완결적으로 담는다.
 */
public record GeneratedPersona(
        Long npcId,
        String name,
        String role,
        Integer age,
        String personality,
        String speechStyle,
        @JsonPropertyDescription("3~5문장 분량의 구체적인 배경 서사. 성격/말투/예시대사에서 자연스럽게 확장할 것")
        String backstory,
        @JsonPropertyDescription("범인이 아니면 null. 범인이면 동기(초안)를 배경과 엮어 구체화한 문장")
        String motive,
        boolean isCulprit,
        @JsonPropertyDescription("이 NPC가 할 법한 예시 대사 3개")
        List<String> sampleLines
) {
}
