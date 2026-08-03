package com.gameproject.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

/** 농사 데이터 (💡 제안 단계, 확정 시 값 갱신 필요). */
@Entity
@Table(name = "crop_master")
@Comment("농사 마스터 데이터 - 작물별 성장일수/체력소모/수확량/가격 (기획 제안 단계, 확정 아님)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CropMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "crop_id")
    private Long cropId;

    @Comment("작물 이름")
    @Column(nullable = false, length = 30)
    private String name;

    @Comment("파종부터 수확까지 걸리는 일수")
    @Column(name = "grow_days", nullable = false)
    private Integer growDays;

    @Comment("파종 1회 또는 수확 1회에 드는 체력 소모량 (파종/수확 공통값)")
    @Column(name = "plant_or_harvest_stamina", nullable = false)
    private Integer plantOrHarvestStamina;

    @Comment("수확 시 얻는 개수")
    @Column(name = "yield_qty", nullable = false)
    private Integer yieldQty;

    @Comment("상점 판매가 (개당)")
    @Column(name = "sell_price", nullable = false)
    private Integer sellPrice;

    @Comment("섭취 시 회복되는 체력")
    @Column(name = "restore_hp", nullable = false)
    private Integer restoreHp;

    @Comment("씨앗 구매가")
    @Column(name = "seed_price", nullable = false)
    private Integer seedPrice;
}
