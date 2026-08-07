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
import jakarta.persistence.Index;
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

/**
 * 대화용 LLM과 주고받은 대화 이력. 세션당 무한정 늘어나는(플레이할수록 계속 쌓이는) 테이블 중
 * 조회가 가장 잦은 편이라(대화창을 열 때마다 findBySessionAndNpcOrderByCreatedAtAsc 호출),
 * session_id 단일 FK 인덱스만으로는 세션이 오래될수록(대화가 많이 쌓일수록) session_id는
 * 맞아도 npc_id로 다시 걸러야 하는 비용이 커진다 — 실제 조회 패턴과 동일한 복합 인덱스를 둔다.
 */
@Entity
@Table(name = "dialogue_log",
        indexes = @Index(name = "idx_dialogue_log_session_npc_created", columnList = "session_id, npc_id, created_at"))
@Comment("대화용 LLM과 주고받은 대화 이력 (사용자/NPC 메시지 각각 1행)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DialogueLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "npc_id", nullable = false)
    private Npc npc;

    @Column(nullable = false)
    private Integer day;

    @Comment("발화자: USER(플레이어) 또는 NPC")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DialogueSender sender;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
