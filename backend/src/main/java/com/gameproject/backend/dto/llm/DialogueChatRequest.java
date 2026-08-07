package com.gameproject.backend.dto.llm;

import java.util.List;

/**
 * backend → llm-proxy: 대화용 LLM 호출 요청.
 * affinityScore는 기획서 확정 스펙(70점 이상 우호적/단서 먼저 제공, 30~70 기본 대화만,
 * 30 미만 회피·단답)을 실제 LLM 응답 태도에 반영하기 위해 현재 호감도 점수를 함께 넘긴다.
 * restrictDetectiveTalk는 기획서 확정 스펙(7일차부터 "간단한 대화만 가능, 추리 대화 불가")을
 * 반영하기 위한 플래그 — true면 사건/단서/범인 추리 관련 질문은 얼버무리고 일상 대화만 응한다.
 * witnessContext/recentVillageEventContext는 DialogueService에서 채워 넣는다 (각각 클래스 주석 참고).
 * witnessIsSecondhand는 witnessContext가 이 NPC 본인의 직접 목격이 아니라 관계망(부부/마을
 * 소식통)을 타고 전해 들은 소문일 때 true — llm-proxy가 "직접 봤다"고 잘못 연기하지 않도록
 * 구분해서 넘긴다 (WitnessGossipService 참고).
 */
public record DialogueChatRequest(
        String personaJson,
        List<DialogueTurn> history,
        String userMessage,
        boolean honestMode,
        int affinityScore,
        boolean restrictDetectiveTalk,
        String witnessContext,
        boolean witnessIsSecondhand,
        String recentVillageEventContext
) {
}
