package com.gameproject.backend.domain;

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

/**
 * 판마다 랜덤 배정되는 범인(culprit)의 사보타주 유형/동기.
 * 기획서상 "범인별 사보타주 대상 풀" 표(💡 최종 확정 아님)에 대응.
 * secondaryType은 기획서에서 방향성만 논의되고 미확정이라 nullable로 둠.
 */
@Entity
@Table(name = "npc_case_assignment")
@Comment("판별 범인(culprit)의 사보타주 유형/동기 배정 (기획 제안 단계, 확정 아님)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NpcCaseAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "npc_id", nullable = false)
    private Npc npc;

    @Comment("주 사보타주 유형 (절도형/파손형/방해공작형)")
    @Enumerated(EnumType.STRING)
    @Column(name = "primary_type", nullable = false, length = 20)
    private SabotageType primaryType;

    @Comment("보조 사보타주 유형 — 기획서상 \"주 80% + 보조 20%\" 방향성만 있고 미확정이라 nullable")
    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_type", length = 20)
    private SabotageType secondaryType;

    @Comment("범행 동기 (페르소나 생성 LLM 입력값)")
    @Lob
    @Column(name = "motive_text", columnDefinition = "TEXT")
    private String motiveText;

    @Comment("사보타주 대상 풀 설명")
    @Lob
    @Column(name = "target_pool_desc", columnDefinition = "TEXT")
    private String targetPoolDesc;
}
