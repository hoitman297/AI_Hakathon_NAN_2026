package com.gameproject.backend.service;

import com.gameproject.backend.domain.NpcCaseAssignment;
import com.gameproject.backend.dto.EndingResponse;

/**
 * AccusationPersistenceService.prepareEnding()의 결과. {@code finished}가 non-null이면
 * (아직 진행 중 / 배드엔딩 / 이미 캐시된 성공 엔딩) 그대로 반환하면 되고 LLM 호출이 필요 없다.
 * null이면 엔딩 스토리를 새로 생성해야 한다는 뜻이고, 그때 필요한 culprit* 필드와 assignment
 * 원본 필드(motiveText/primaryType/targetPoolDesc)가 채워져 있다. culpritNpcId로 별도 조회한
 * culprit은 지연 로딩 프록시가 아니라 완전히 로드된 엔티티라 트랜잭션 밖에서도 안전하다.
 */
record EndingOutcome(
        EndingResponse finished,
        Long culpritNpcId,
        String culpritName,
        String culpritRole,
        Integer culpritAge,
        String culpritPersonalityDesc,
        String culpritSpeechStyle,
        String motiveText,
        String primaryType,
        String targetPoolDesc,
        NpcCaseAssignment assignment) {

    static EndingOutcome finished(EndingResponse response) {
        return new EndingOutcome(response, null, null, null, null, null, null, null, null, null, null);
    }
}
