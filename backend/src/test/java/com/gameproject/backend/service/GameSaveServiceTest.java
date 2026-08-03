package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameproject.backend.domain.Account;
import com.gameproject.backend.domain.GameSave;
import com.gameproject.backend.dto.SaveRequest;
import com.gameproject.backend.dto.SaveResponse;
import com.gameproject.backend.repository.GameSaveRepository;

@ExtendWith(MockitoExtension.class)
class GameSaveServiceTest {

    @Mock
    private GameSaveRepository gameSaveRepository;

    private GameSaveService gameSaveService;
    private Account account;

    @BeforeEach
    void setUp() {
        gameSaveService = new GameSaveService(gameSaveRepository, new ObjectMapper());
        account = Account.builder().accountId(1L).username("u").passwordHash("h").nickname("n")
                .createdAt(LocalDateTime.now()).build();
    }

    @Test
    void save_firstTime_createsNewSaveWithDefaultEndingState() {
        when(gameSaveRepository.findById(1L)).thenReturn(Optional.empty());
        when(gameSaveRepository.save(any(GameSave.class))).thenAnswer(inv -> inv.getArgument(0));

        SaveRequest request = new SaveRequest(3, "day", 80, Map.of("씨앗", 2), Map.of("나박수", 60),
                List.of("clue1"), null, List.of(), 1, Map.of());

        SaveResponse response = gameSaveService.save(account, request);

        assertThat(response.endingState()).isEqualTo("in_progress");
        assertThat(response.saveData()).isEqualTo(request);

        ArgumentCaptor<GameSave> captor = ArgumentCaptor.forClass(GameSave.class);
        verify(gameSaveRepository).save(captor.capture());
        assertThat(captor.getValue().getAccount()).isEqualTo(account);
        assertThat(captor.getValue().getEndingState()).isEqualTo("in_progress");
    }

    @Test
    void save_existingSave_resetsEndingStateToInProgress() {
        // ending_state 갱신 방법이 별도로 정의되지 않아, save()는 항상 "in_progress"로 되돌리는
        // 것이 현재 의도된 동작이다 (TODO.md 7번 참고).
        GameSave existing = GameSave.builder().accountId(1L).account(account)
                .saveData("{}").endingState(GameSaveService.ENDING_STATE_SUCCESS)
                .updatedAt(LocalDateTime.now().minusDays(1)).build();
        when(gameSaveRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(gameSaveRepository.save(any(GameSave.class))).thenAnswer(inv -> inv.getArgument(0));

        SaveRequest request = new SaveRequest(5, "night", 50, Map.of(), Map.of(), List.of(), null, List.of(), 2, Map.of());
        SaveResponse response = gameSaveService.save(account, request);

        assertThat(response.endingState()).isEqualTo("in_progress");
        assertThat(existing.getEndingState()).isEqualTo("in_progress");
    }

    @Test
    void load_noSave_returnsDefaults() {
        when(gameSaveRepository.findById(1L)).thenReturn(Optional.empty());

        SaveResponse response = gameSaveService.load(account);

        assertThat(response.endingState()).isEqualTo("in_progress");
        assertThat(response.updatedAt()).isNull();
        assertThat(response.saveData().day()).isEqualTo(1);
        assertThat(response.saveData().phase()).isEqualTo("day");
        assertThat(response.saveData().playerHp()).isEqualTo(100);
    }

    @Test
    void load_existingSave_deserializesStoredJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SaveRequest stored = new SaveRequest(4, "day", 70, Map.of("작물", 1), Map.of(), List.of(), "npc-1", List.of(), 0, Map.of());
        String json = mapper.writeValueAsString(stored);
        LocalDateTime updatedAt = LocalDateTime.now();
        GameSave existing = GameSave.builder().accountId(1L).account(account)
                .saveData(json).endingState("success").updatedAt(updatedAt).build();
        when(gameSaveRepository.findById(1L)).thenReturn(Optional.of(existing));

        SaveResponse response = gameSaveService.load(account);

        assertThat(response.saveData()).isEqualTo(stored);
        assertThat(response.endingState()).isEqualTo("success");
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void syncEndingState_existingSave_updatesEndingStateOnly() {
        GameSave existing = GameSave.builder().accountId(1L).account(account)
                .saveData("{}").endingState("in_progress").updatedAt(LocalDateTime.now()).build();
        when(gameSaveRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(gameSaveRepository.save(any(GameSave.class))).thenAnswer(inv -> inv.getArgument(0));

        gameSaveService.syncEndingState(account, GameSaveService.ENDING_STATE_SUCCESS);

        assertThat(existing.getEndingState()).isEqualTo(GameSaveService.ENDING_STATE_SUCCESS);
        verify(gameSaveRepository).save(existing);
    }

    @Test
    void syncEndingState_noExistingSave_doesNothing() {
        when(gameSaveRepository.findById(1L)).thenReturn(Optional.empty());

        gameSaveService.syncEndingState(account, GameSaveService.ENDING_STATE_BAD_ENDING);

        verify(gameSaveRepository, never()).save(any());
    }
}
