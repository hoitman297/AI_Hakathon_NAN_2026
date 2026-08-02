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

/** 7~8일차(마을/주민 대상), 8~9일차(플레이어 대상) 오답 시 랜덤 이벤트. */
@Entity
@Table(name = "random_event_log")
@Comment("오답 고발 시 발생하는 랜덤 이벤트 (7~8일차 마을/주민 대상, 8~9일차 플레이어 대상)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RandomEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @Comment("이벤트 발생 일차")
    @Column(nullable = false)
    private Integer day;

    @Comment("이벤트 대상: 마을/주민(VILLAGE) 또는 플레이어(PLAYER)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EventTarget target;

    @Comment("이벤트 종류 (예: 협박 편지, 밭 훼손)")
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
