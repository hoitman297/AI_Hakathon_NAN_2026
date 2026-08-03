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

/** 단서 카드 (판당 총 5장, 사보타주 발생 장소에서 습득, 놓쳐도 계속 남음). */
@Entity
@Table(name = "clue_card")
@Comment("단서 카드 - 판당 총 5장, 사보타주 발생 장소에서 습득 (자동 지급 아님)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClueCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "clue_id")
    private Long clueId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sabotage_event_id", nullable = false)
    private SabotageEvent sabotageEvent;

    @Comment("단서 주제: 머리카락/소지품/발자국/혈흔/자국")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClueTopic topic;

    @Comment("애매하게 설계된 원본 단서 문구")
    @Lob
    @Column(name = "text_ambiguous", nullable = false, columnDefinition = "TEXT")
    private String textAmbiguous;

    @Comment("돋보기 사용 후 갱신되는 명확화된 문구 (1일 1회 제한은 서비스 로직에서 처리)")
    @Lob
    @Column(name = "text_clarified", columnDefinition = "TEXT")
    private String textClarified;

    @Comment("플레이어가 실제로 습득했는지 여부 (자동 지급 아님)")
    @Column(name = "is_acquired", nullable = false)
    private Boolean acquired;

    @Column(name = "acquired_at")
    private LocalDateTime acquiredAt;
}
