package com.gameproject.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.InventoryItem;
import com.gameproject.backend.domain.InventoryItemType;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    List<InventoryItem> findBySession(GameSession session);

    Optional<InventoryItem> findBySessionAndSlotIndex(GameSession session, Integer slotIndex);

    Optional<InventoryItem> findBySessionAndItemTypeAndItemRefId(
            GameSession session, InventoryItemType itemType, Long itemRefId);
}
