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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

/** 밤에 발생하는 사보타주 사건 기록 (1~5일차 밤, 총 5회). */
@Entity
@Table(name = "sabotage_event")
@Comment("밤마다 발생하는 사보타주 사건 기록 (1~5일차 밤, 판당 총 5회)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SabotageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @Comment("발생 일차 (1~5)")
    @Column(nullable = false)
    private Integer day;

    @Comment("사건 발생 장소")
    @Column(nullable = false, length = 50)
    private String location;

    @Comment("절도형/파손형/방해공작형")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SabotageType type;

    @Column(name = "sub_target", length = 100)
    private String subTarget;

    @Comment("범인이 본인 소유물을 스스로 터는 척하는 특수 케이스 여부")
    @Column(name = "is_self_decoy", nullable = false)
    private Boolean selfDecoy;

    @Comment("낮 동선과 목격담으로 연결되는 목격 NPC (nullable)")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "witness_npc_id")
    private Npc witnessNpc;

    @Comment("다음날 아침 화면에 노출되는 짧은 연출 문구(LLM 생성) — 범인 정보는 포함하지 않음")
    @Lob
    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
