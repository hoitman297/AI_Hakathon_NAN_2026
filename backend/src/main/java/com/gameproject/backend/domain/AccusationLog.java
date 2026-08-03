package com.gameproject.backend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

/** 고발(범인 지목) 시도 이력 — 7/8/9일차에만 발생 가능. */
@Entity
@Table(name = "accusation_log")
@Comment("고발(범인 지목) 시도 이력 - 7~9일차에만 발생 가능")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccusationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "accusation_id")
    private Long accusationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @Comment("고발한 일차 (7~9)")
    @Column(nullable = false)
    private Integer day;

    @Comment("지목한 NPC")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accused_npc_id", nullable = false)
    private Npc accusedNpc;

    @Comment("정답 여부")
    @Column(name = "is_correct", nullable = false)
    private Boolean correct;

    @Column(name = "resolved_at", nullable = false)
    private LocalDateTime resolvedAt;
}
