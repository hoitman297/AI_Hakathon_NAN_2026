package com.gameproject.llmproxy.dto;

import java.util.List;

/**
 * affinityScore는 기획서 확정 스펙(70점 이상 우호적/단서 먼저 제공, 30~70 기본 대화만,
 * 30 미만 회피·단답)을 실제 LLM 응답 태도에 반영하기 위한 현재 호감도 점수(0~100).
 * restrictDetectiveTalk는 기획서 확정 스펙(7일차부터 "간단한 대화만 가능, 추리 대화 불가")을
 * 반영하기 위한 플래그 — true면 사건/단서/범인 추리 관련 질문은 얼버무리고 일상 대화만 응한다.
 */
public record DialogueChatRequest(
        String personaJson,
        List<DialogueTurn> history,
        String userMessage,
        boolean honestMode,
        int affinityScore,
        boolean restrictDetectiveTalk
) {
}
