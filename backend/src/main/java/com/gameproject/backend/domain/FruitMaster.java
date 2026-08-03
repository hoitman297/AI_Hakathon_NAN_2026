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

/** 채집 데이터 (💡 제안 단계, 확정 시 값 갱신 필요). */
@Entity
@Table(name = "fruit_master")
@Comment("채집 마스터 데이터 - 과일별 재생주기/체력소모/가격 (기획 제안 단계, 확정 아님)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FruitMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fruit_id")
    private Long fruitId;

    @Comment("과일 이름")
    @Column(nullable = false, length = 30)
    private String name;

    @Comment("채집 후 다시 채집 가능해지기까지의 쿨다운 일수")
    @Column(name = "regen_days", nullable = false)
    private Integer regenDays;

    @Comment("채집 1회당 체력 소모량")
    @Column(name = "forage_stamina", nullable = false)
    private Integer forageStamina;

    @Comment("섭취 시 회복되는 체력")
    @Column(name = "restore_hp", nullable = false)
    private Integer restoreHp;

    @Comment("상점 판매가 (개당)")
    @Column(name = "sell_price", nullable = false)
    private Integer sellPrice;
}
