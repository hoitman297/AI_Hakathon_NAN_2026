package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameproject.backend.domain.Account;
import com.gameproject.backend.domain.GameSave;
import com.gameproject.backend.dto.SaveRequest;
import com.gameproject.backend.dto.SaveResponse;
import com.gameproject.backend.repository.GameSaveRepository;

import lombok.RequiredArgsConstructor;

/**
 * 계정별 게임 세이브 스냅샷 저장/조회. 기존 정규화 테이블·서비스(GameSession 등)와는
 * 완전히 독립적이며, save_data는 JSON 컬럼 타입 없이 LONGTEXT + Jackson 수동
 * 직렬화/역직렬화로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class GameSaveService {

    /** ending_state는 원 요구사항에 갱신 방법이 정의되어 있지 않아, 지금은 항상 기본값으로 저장한다. */
    private static final String DEFAULT_ENDING_STATE = "in_progress";

    private final GameSaveRepository gameSaveRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SaveResponse save(Account account, SaveRequest request) {
        String json = serialize(request);

        GameSave save = gameSaveRepository.findById(account.getAccountId())
                .orElseGet(() -> GameSave.builder().account(account).build());
        save.setSaveData(json);
        save.setEndingState(DEFAULT_ENDING_STATE);
        save.setUpdatedAt(LocalDateTime.now());
        GameSave saved = gameSaveRepository.save(save);

        return new SaveResponse(request, saved.getEndingState(), saved.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public SaveResponse load(Account account) {
        return gameSaveRepository.findById(account.getAccountId())
                .map(save -> new SaveResponse(deserialize(save.getSaveData()), save.getEndingState(), save.getUpdatedAt()))
                .orElseGet(this::defaultSave);
    }

    /** 저장된 세이브가 없을 때(신규 플레이어) 반환하는 기본값. */
    private SaveResponse defaultSave() {
        SaveRequest fresh = new SaveRequest(
                1, "day", 100,
                Map.of(), Map.of(), List.of(), null, List.of(), 0, Map.of());
        return new SaveResponse(fresh, DEFAULT_ENDING_STATE, null);
    }

    private String serialize(SaveRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("세이브 데이터 직렬화에 실패했습니다.", e);
        }
    }

    private SaveRequest deserialize(String json) {
        try {
            return objectMapper.readValue(json, SaveRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 세이브 데이터가 손상되었습니다.", e);
        }
    }
}
