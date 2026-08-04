package com.gameproject.backend.domain;

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

/**
 * 일자별 플레이어 상태.
 * staminaMax/일일 시작 체력치는 기획서에 명시가 없어(기절 리스폰값 70만 명시)
 * 잠정적으로 max=100, 1일차 시작=100으로 둠 — 기획 확정 후 조정 필요.
 */
@Entity
@Table(name = "player_stat", uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "day"}))
@Comment("일자별 플레이어 상태 (체력/골드/기절여부). 체력 최대치는 기획 미확정, 잠정값 100")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @Comment("일차 (1~9)")
    @Column(nullable = false)
    private Integer day;

    @Comment("현재 체력 (이동 체력 소모가 초당 연속형이라 소수점 정밀도가 필요함)")
    @Column(name = "stamina_current", nullable = false)
    private Double staminaCurrent;

    @Comment("최대 체력 (기획 미확정, 잠정값 100)")
    @Column(name = "stamina_max", nullable = false)
    private Integer staminaMax;

    @Comment("보유 골드")
    @Column(nullable = false)
    private Integer gold;

    @Comment("이날 기절했는지 여부 (체력 0 도달)")
    @Column(nullable = false)
    private Boolean fainted;

    @Version
    private Long version;
}
