package com.gameproject.backend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

/**
 * 목격담이 원래 목격자를 넘어 관계망(부부·마을 소식통 등)을 타고 전해진 기록.
 * 최초 기획 초안에는 없던 구현 보완 테이블 — SabotageEvent.witnessNpc(직접 목격자)와
 * 별개로, "전해 들어서 아는" NPC를 세션별로 추가 기록한다. 새 정보를 생성하지 않고
 * SabotageEvent.summaryText/목격담 문맥을 그대로 재사용하므로 범인 특정 정보가
 * 늘어나지는 않는다 — 오직 "누가 이 소문을 들었는지"만 관계 그래프를 따라 넓어진다.
 *
 * <p>uniqueConstraints의 (sabotage_event_id, npc_id) 인덱스는 WitnessGossipService의
 * 중복 전파 방지용이고, DialogueChatPersistenceService가 대화마다(목격자가 아닌 NPC와
 * 대화할 때마다) 던지는 findBySessionAndNpc는 그 인덱스의 선두 컬럼이 달라 못 탄다 —
 * 실제 조회 패턴에 맞춘 별도 인덱스를 둔다.
 */
@Entity
@Table(name = "npc_witness_awareness",
        uniqueConstraints = @UniqueConstraint(columnNames = {"sabotage_event_id", "npc_id"}),
        indexes = @Index(name = "idx_witness_awareness_session_npc", columnList = "session_id, npc_id"))
@Comment("목격담이 관계망을 타고 2차 전파된 기록 (최초 기획 초안에는 없던 구현 보완 테이블)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NpcWitnessAwareness {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sabotage_event_id", nullable = false)
    private SabotageEvent sabotageEvent;

    @Comment("이 목격담을 전해 들어서 알게 된 NPC (원래 목격자 본인은 포함하지 않음)")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "npc_id", nullable = false)
    private Npc npc;

    @Comment("전해 들은 일차")
    @Column(name = "learned_day", nullable = false)
    private Integer learnedDay;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
