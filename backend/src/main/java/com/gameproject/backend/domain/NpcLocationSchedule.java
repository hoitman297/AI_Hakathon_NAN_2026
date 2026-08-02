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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

/** 낮 동선 배치표 (💡 제안 단계, 확정 시 값 갱신 필요). */
@Entity
@Table(name = "npc_location_schedule")
@Comment("낮 동선 배치표 - NPC별 위치 등장 확률 (기획 제안 단계, 확정 아님)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NpcLocationSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "npc_id", nullable = false)
    private Npc npc;

    @Comment("기본 위치(PRIMARY) / 보조 위치(SECONDARY)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LocationSlot slot;

    @Comment("장소 이름 (예: 마을회관, 상점)")
    @Column(name = "location_name", nullable = false, length = 50)
    private String locationName;

    @Comment("등장 확률 (0~100, %)")
    @Column(nullable = false)
    private Integer probability;
}
