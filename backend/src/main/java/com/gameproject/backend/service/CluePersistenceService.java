package com.gameproject.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.domain.ClueCard;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.InventoryItemType;
import com.gameproject.backend.domain.NpcCaseAssignment;
import com.gameproject.backend.domain.ShopItemCode;
import com.gameproject.backend.domain.ShopItemMaster;
import com.gameproject.backend.dto.ClueCardResponse;
import com.gameproject.backend.repository.ClueCardRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.NpcCaseAssignmentRepository;
import com.gameproject.backend.repository.ShopItemMasterRepository;

import lombok.RequiredArgsConstructor;

/**
 * ClueService.clarify()의 DB 읽기/쓰기 전용 조각. clarifyClueContent() LLM 호출을 트랜잭션
 * 밖에서 하기 위해 분리했다 — 이유는 DialogueChatPersistenceService 클래스 주석 참고
 * (self-invocation 때문에 같은 클래스 안에서는 안 되고 별도 빈이어야 한다).
 */
@Service
@RequiredArgsConstructor
class CluePersistenceService {

    private final ClueCardRepository clueCardRepository;
    private final ShopItemMasterRepository shopItemMasterRepository;
    private final GameSessionRepository sessionRepository;
    private final InventoryService inventoryService;
    private final SessionService sessionService;
    private final NpcCaseAssignmentRepository caseAssignmentRepository;

    @Transactional
    ClarifyContext prepareClarify(Long sessionId, Long clueId) {
        GameSession session = sessionService.findSession(sessionId);
        ClueCard clue = getOwnedClue(session, clueId);
        if (!Boolean.TRUE.equals(clue.getAcquired())) {
            throw new IllegalStateException("아직 습득하지 않은 단서입니다.");
        }
        if (clue.getTextClarified() != null) {
            throw new IllegalStateException("이미 명확화된 단서입니다.");
        }
        if (session.getLastMagnifierUseDay() != null
                && session.getLastMagnifierUseDay().equals(session.getCurrentDay())) {
            throw new IllegalStateException("돋보기는 하루에 한 번만 사용할 수 있습니다.");
        }

        ShopItemMaster magnifier = shopItemMasterRepository.findByItemCode(ShopItemCode.MAGNIFIER)
                .orElseThrow(() -> new IllegalStateException("돋보기 마스터 데이터가 없습니다."));
        inventoryService.removeItem(session, InventoryItemType.SHOP_ITEM, magnifier.getItemId(), 1);

        session.setLastMagnifierUseDay(session.getCurrentDay());
        sessionRepository.save(session);

        NpcCaseAssignment assignment = caseAssignmentRepository.findBySession(session)
                .orElseThrow(() -> new IllegalStateException("범인 배정 정보가 없습니다."));
        // assignment.getNpc()는 지연 로딩이라 이 트랜잭션이 열려 있는 동안에만 접근 가능 —
        // appearanceDesc(순수 문자열)로 미리 꺼내서 트랜잭션 밖(LLM 호출 구간)에도 안전하게 넘긴다.
        String appearanceDesc = assignment.getNpc().getAppearanceDesc();

        return new ClarifyContext(clue, clue.getTopic().name(), appearanceDesc, clue.getTextAmbiguous());
    }

    @Transactional
    ClueCardResponse saveClarifiedText(ClueCard clue, String clarifiedText) {
        clue.setTextClarified(clarifiedText);
        return ClueService.toResponse(clueCardRepository.save(clue));
    }

    private ClueCard getOwnedClue(GameSession session, Long clueId) {
        ClueCard clue = clueCardRepository.findById(clueId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 단서입니다: " + clueId));
        if (!clue.getSession().getSessionId().equals(session.getSessionId())) {
            throw new IllegalArgumentException("이 세션의 단서가 아닙니다.");
        }
        return clue;
    }
}
