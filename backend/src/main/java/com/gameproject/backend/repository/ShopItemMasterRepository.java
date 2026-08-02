package com.gameproject.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.ShopItemMaster;

public interface ShopItemMasterRepository extends JpaRepository<ShopItemMaster, Long> {

    Optional<ShopItemMaster> findByName(String name);
}
