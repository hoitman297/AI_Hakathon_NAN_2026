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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

/** 인벤토리 슬롯 (판당 7칸 고정). */
@Entity
@Table(name = "inventory_item")
@Comment("인벤토리 슬롯 (판당 7칸 고정)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @Comment("슬롯 번호 (1~7)")
    @Column(name = "slot_index", nullable = false)
    private Integer slotIndex;

    @Comment("CROP/FRUIT/SHOP_ITEM 중 어떤 마스터를 참조하는지")
    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private InventoryItemType itemType;

    @Comment("item_type에 따라 crop_master / fruit_master / shop_item_master의 PK를 참조 (FK 아님, 애플리케이션에서 분기 처리)")
    @Column(name = "item_ref_id", nullable = false)
    private Long itemRefId;

    @Column(nullable = false)
    private Integer quantity;

    @Version
    private Long version;
}
