package com.gameproject.backend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

/** 한 판(플레이스루) 단위 상태. */
@Entity
@Table(name = "game_session")
@Comment("게임 세션 - 한 판(플레이스루) 단위 상태. 게임의 최상위 기준 테이블")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @Comment("로그인 없이 플레이하는 게스트용 임시 식별자 (디바이스 ID 등). 로그인 세션은 account로 식별.")
    @Column(name = "player_id", length = 100)
    private String playerId;

    @Comment("세션 소유 계정 (로그인 필수 — 게스트 플레이 없음)")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Comment("이번 판의 범인으로 랜덤 배정된 NPC")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "culprit_npc_id", nullable = false)
    private Npc culprit;

    @Comment("현재 진행 일차 (1~9)")
    @Column(name = "current_day", nullable = false)
    private Integer currentDay;

    @Comment("진행중(IN_PROGRESS) / 성공(SUCCESS) / 배드엔딩(BAD_ENDING)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Comment("운동화 구매 후 장착 여부 (이동 체력 소모 5→4). 이동 API는 별도 미확정이라 값만 보관")
    @Column(name = "sneakers_equipped", nullable = false)
    private Boolean sneakersEquipped;

    @Comment("거짓말탐지기 사용 시 \"정직 모드\"가 적용될 다음 대화 상대 NPC (1턴짜리 임시 효과)")
    @Column(name = "honest_mode_npc_id")
    private Long honestModeNpcId;

    @Comment("정직 모드가 적용되는 일차 (honest_mode_npc_id와 함께 사용)")
    @Column(name = "honest_mode_day")
    private Integer honestModeDay;

    @Comment("거짓말탐지기를 마지막으로 사용한 일차 (\"1일 1회\" 제한 체크용, honest_mode_day와 별개)")
    @Column(name = "last_lie_detector_use_day")
    private Integer lastLieDetectorUseDay;

    @Comment("돋보기를 마지막으로 사용한 일차 (\"1일 1회\" 제한 체크용)")
    @Column(name = "last_magnifier_use_day")
    private Integer lastMagnifierUseDay;

    @Version
    private Long version;
}
