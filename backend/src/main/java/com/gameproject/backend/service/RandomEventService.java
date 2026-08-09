package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.RandomEventLog;
import com.gameproject.backend.dto.RandomEventResponse;
import com.gameproject.backend.repository.RandomEventLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * 7~8일차(마을 대상)/8~9일차(플레이어 대상) 랜덤 이벤트 조회 및 확인(view) 처리.
 * ClueService(습득/acquire)와 같은 패턴 — 이벤트 자체는 오답 고발 시점에 이미
 * AccusationPersistenceService.saveRandomEvent()가 만들어두고, 여기서는 그걸
 * 플레이어가 "확인했는지"만 관리한다. LLM을 호출하지 않으므로 AccusationService처럼
 * 나눌 필요 없이 단일 클래스로 충분하다.
 */
@Service
@RequiredArgsConstructor
public class RandomEventService {

    private final RandomEventLogRepository randomEventLogRepository;
    private final SessionService sessionService;

    @Transactional(readOnly = true)
    public List<RandomEventResponse> listUnviewed(Long sessionId) {
        GameSession session = sessionService.findSession(sessionId);
        return randomEventLogRepository.findBySessionAndViewedFalse(session).stream()
                .map(RandomEventService::toResponse)
                .toList();
    }

    @Transactional
    public RandomEventResponse markViewed(Long sessionId, Long eventId) {
        GameSession session = sessionService.findSession(sessionId);
        RandomEventLog event = randomEventLogRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이벤트입니다: " + eventId));
        if (!event.getSession().getSessionId().equals(session.getSessionId())) {
            throw new IllegalArgumentException("이 세션의 이벤트가 아닙니다.");
        }
        if (!Boolean.TRUE.equals(event.getViewed())) {
            event.setViewed(true);
            event.setViewedAt(LocalDateTime.now());
        }
        return toResponse(randomEventLogRepository.save(event));
    }

    private static RandomEventResponse toResponse(RandomEventLog event) {
        return new RandomEventResponse(
                event.getEventId(), event.getDay(), event.getTarget().name(),
                event.getEventType(), event.getDescription(), event.getViewed());
    }
}
