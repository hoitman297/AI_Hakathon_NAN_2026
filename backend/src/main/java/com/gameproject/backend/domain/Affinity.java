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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

/** 사용자-NPC 간 호감도 (NPC끼리의 관계와는 별개, 0~100, 시작값 50). */
@Entity
@Table(name = "affinity", uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "npc_id"}))
@Comment("사용자-NPC 간 호감도 (NPC끼리의 관계와는 별개 개념, 0~100, 시작값 50)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Affinity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "npc_id", nullable = false)
    private Npc npc;

    @Comment("호감도 점수 (0~100, 시작값 50)")
    @Column(nullable = false)
    private Integer score;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
