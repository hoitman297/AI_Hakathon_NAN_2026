package com.gameproject.backend.domain;

public enum InventoryItemType {
    CROP,       // crop_master 참조 (수확물)
    FRUIT,      // fruit_master 참조 (채집물)
    SHOP_ITEM,  // shop_item_master 참조 (구매 아이템)
    SEED        // crop_master 참조 (상점에서 구매했지만 아직 밭에 심지 않은 씨앗)
}
