package com.gameproject.backend.service;

/**
 * InventoryPersistenceService.prepareUseItem()의 결과. {@code finishedMessage}가
 * non-null이면(선물세트 이외의 모든 아이템) DB 쓰기까지 이미 다 끝난 상태라 그대로 반환하면
 * 되고 LLM 호출이 필요 없다. null이면 선물세트를 사용해 NPC 반응 생성이 필요하다는 뜻이고,
 * 그때 필요한 target* 필드(지연 로딩 프록시가 아니라 npcRepository로 직접 조회한, 완전히
 * 로드된 엔티티에서 꺼낸 값)가 채워져 있다 — 호감도 반영/아이템 소모 등 DB 쓰기는 이미
 * 이 시점에 전부 커밋되어 있고, 이후 LLM 호출 결과는 메시지 문구에만 쓰이고 별도로
 * 저장되지 않는다.
 */
record UseItemOutcome(
        String finishedMessage,
        String targetName,
        String targetRole,
        Integer targetAge,
        String targetPersonalityDesc,
        String targetSpeechStyle,
        String targetSampleLine,
        int gain,
        int newAffinityScore) {

    static UseItemOutcome finished(String message) {
        return new UseItemOutcome(message, null, null, null, null, null, null, 0, 0);
    }
}
