package com.gameproject.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.ShopItemCode;
import com.gameproject.backend.domain.ShopItemMaster;

public interface ShopItemMasterRepository extends JpaRepository<ShopItemMaster, Long> {

    /** 상점 아이템 마스터 데이터는 서버 기동 시 시딩된 후 절대 안 바뀐다 — CacheConfig 참고. */
    @Override
    @Cacheable("shopItems")
    List<ShopItemMaster> findAll();

    @Override
    @Cacheable("shopItemsById")
    Optional<ShopItemMaster> findById(Long itemId);

    @Cacheable("shopItemsByCode")
    Optional<ShopItemMaster> findByItemCode(ShopItemCode itemCode);
}
