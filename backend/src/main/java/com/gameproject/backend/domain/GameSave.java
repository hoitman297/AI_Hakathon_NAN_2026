package com.gameproject.backend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

/**
 * 계정별 게임 세이브 스냅샷 (1계정 1세이브). 기존 정규화 테이블(GameSession,
 * PlayerStat, InventoryItem, Affinity, ClueCard 등)과는 무관한 독립 저장 기능 —
 * 이 테이블은 그 테이블들을 대체하지 않는다.
 * save_data는 JSON 컬럼 타입/TypeHandler를 쓰지 않고 LONGTEXT로 저장하며,
 * 직렬화/역직렬화는 GameSaveService에서 Jackson ObjectMapper로 직접 처리한다.
 */
@Entity
@Table(name = "game_save")
@Comment("계정별 게임 세이브 스냅샷 (독립 기능, 정규화 테이블과 무관)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameSave {

    @Id
    @Comment("세이브 소유 계정 ID (PK이자 FK — 1계정 1세이브)")
    @Column(name = "account_id")
    private Long accountId;

    /** account_id 컬럼을 accountId(@Id)와 공유하는 연관관계 — @Comment는 accountId 쪽에만 붙인다. */
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "account_id")
    private Account account;

    @Comment("게임 진행 상태 JSON 문자열 (day/phase/inventory/affection/clues 등)")
    @Lob
    @Column(name = "save_data", columnDefinition = "LONGTEXT", nullable = false)
    private String saveData;

    @Comment("in_progress / success / bad_ending")
    @Column(name = "ending_state", nullable = false, length = 20)
    private String endingState;

    @Comment("마지막 저장 시각 (저장할 때마다 서비스에서 갱신)")
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
