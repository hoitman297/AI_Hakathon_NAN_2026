package com.gameproject.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

/** 상점 아이템 4종 (가방/인벤토리 확장 아이템은 기획서상 제외 대상). */
@Entity
@Table(name = "shop_item_master")
@Comment("상점 아이템 마스터 데이터 (운동화/거짓말탐지기/돋보기/선물세트)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopItemMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @Comment("아이템 이름 (화면 표시용 — 게임 로직에서는 item_code로 식별할 것)")
    @Column(nullable = false, length = 30)
    private String name;

    @Comment("게임 로직에서 아이템을 식별하는 코드 (표시 이름과 분리 — 이름이 바뀌어도 로직에 영향 없음)")
    @Enumerated(EnumType.STRING)
    @Column(name = "item_code", nullable = false, length = 20)
    private ShopItemCode itemCode;

    @Comment("영구 장비(PERMANENT_EQUIPMENT) 또는 소모품(CONSUMABLE)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ItemCategory category;

    @Comment("구매 가격")
    @Column(nullable = false)
    private Integer price;

    @Comment("효과 설명")
    @Lob
    @Column(name = "effect_desc", columnDefinition = "TEXT")
    private String effectDesc;

    @Comment("사용 제한 설명 (예: \"1회 구매, 영구\" / \"1일 1회\" / \"1개당 1회\")")
    @Column(name = "usage_limit", length = 50)
    private String usageLimit;
}
