package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.client.LlmProxyClient;
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

@Service
@RequiredArgsConstructor
public class ClueService {

    private final ClueCardRepository clueCardRepository;
    private final ShopItemMasterRepository shopItemMasterRepository;
    private final GameSessionRepository sessionRepository;
    private final InventoryService inventoryService;
    private final SessionService sessionService;
    private final NpcCaseAssignmentRepository caseAssignmentRepository;
    private final LlmProxyClient llmProxyClient;

    @Transactional(readOnly = true)
    public List<ClueCardResponse> listAcquired(Long sessionId) {
        GameSession session = sessionService.findSession(sessionId);
        return clueCardRepository.findBySessionAndAcquiredTrue(session).stream()
                .map(this::toResponse)
                .toList();
    }

    /** 사보타주 발생 장소에 가서 습득하는 동작 (지도/이동은 클라이언트 담당, 여기선 상태만 기록) */
    @Transactional
    public ClueCardResponse acquire(Long sessionId, Long clueId) {
        ClueCard clue = getOwnedClue(sessionId, clueId);
        if (Boolean.TRUE.equals(clue.getAcquired())) {
            return toResponse(clue);
        }
        clue.setAcquired(true);
        clue.setAcquiredAt(LocalDateTime.now());
        return toResponse(clueCardRepository.save(clue));
    }

    /** 돋보기 1개를 소모해서 단서 문구를 더 명확하게 갱신 (1일 1회, 이미 명확화된 단서는 재사용 불가) */
    @Transactional
    public ClueCardResponse clarify(Long sessionId, Long clueId) {
        GameSession session = sessionService.findSession(sessionId);
        ClueCard clue = getOwnedClue(sessionId, clueId);
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
        String clarifiedText = llmProxyClient.clarifyClueContent(
                clue.getTopic().name(), assignment.getNpc().getAppearanceDesc(), clue.getTextAmbiguous());
        clue.setTextClarified(clarifiedText);
        return toResponse(clueCardRepository.save(clue));
    }

    private ClueCard getOwnedClue(Long sessionId, Long clueId) {
        GameSession session = sessionService.findSession(sessionId);
        ClueCard clue = clueCardRepository.findById(clueId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 단서입니다: " + clueId));
        if (!clue.getSession().getSessionId().equals(session.getSessionId())) {
            throw new IllegalArgumentException("이 세션의 단서가 아닙니다.");
        }
        return clue;
    }

    private ClueCardResponse toResponse(ClueCard clue) {
        String text = clue.getTextClarified() != null ? clue.getTextClarified() : clue.getTextAmbiguous();
        return new ClueCardResponse(clue.getClueId(), clue.getTopic().name(), text, clue.getTextClarified() != null);
    }
}
