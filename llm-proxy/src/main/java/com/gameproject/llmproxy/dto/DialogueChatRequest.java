package com.gameproject.llmproxy.dto;

import java.util.List;

/**
 * affinityScore는 기획서 확정 스펙(70점 이상 우호적/단서 먼저 제공, 30~70 기본 대화만,
 * 30 미만 회피·단답)을 실제 LLM 응답 태도에 반영하기 위한 현재 호감도 점수(0~100).
 * restrictDetectiveTalk는 기획서 확정 스펙(7일차부터 "간단한 대화만 가능, 추리 대화 불가")을
 * 반영하기 위한 플래그 — true면 사건/단서/범인 추리 관련 질문은 얼버무리고 일상 대화만 응한다.
 * witnessContext는 이 NPC가 목격자로 배정된 밤이 있으면 "N일차 밤 장소" 형태로 넘어온다 —
 * 기획서의 "낮 동선과 밤 사보타주를 목격담으로 연결" 요구사항을 실제 대사로 드러내기 위함
 * (범인이 누구인지는 이 NPC도 모르므로 절대 특정하지 않는다).
 * recentVillageEventContext는 최근 마을 전체 대상 랜덤 이벤트(7→8일차) 설명 — 마을 사람이면
 * 누구나 알 법한 공개 사건이라 자연스러운 대화 소재로 넘긴다. 플레이어 대상 이벤트(8→9일차)는
 * 플레이어 개인 문제라 NPC가 알 리 없으므로 여기 포함하지 않는다.
 */
public record DialogueChatRequest(
        String personaJson,
        List<DialogueTurn> history,
        String userMessage,
        boolean honestMode,
        int affinityScore,
        boolean restrictDetectiveTalk,
        String witnessContext,
        String recentVillageEventContext
) {
}
