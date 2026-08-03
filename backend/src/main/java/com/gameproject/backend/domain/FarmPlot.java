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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

/**
 * 파종된 작물의 재배 상태.
 * 최초 설계 초안(기획서 분석 문서)에는 없었으나, 파종→N일 성장→수확 흐름을
 * 실제로 구현하려면 필요해서 추가한 테이블.
 */
@Entity
@Table(name = "farm_plot")
@Comment("파종된 작물의 재배 상태 (파종→성장→수확). 최초 기획 초안에는 없던 구현 보완 테이블")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmPlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_id", nullable = false)
    private CropMaster crop;

    @Comment("파종한 일차")
    @Column(name = "planted_day", nullable = false)
    private Integer plantedDay;

    @Comment("수확 가능해지는 일차 (planted_day + crop.grow_days)")
    @Column(name = "ready_day", nullable = false)
    private Integer readyDay;

    @Comment("수확 완료 여부")
    @Column(nullable = false)
    private Boolean harvested;

    @Version
    private Long version;
}
