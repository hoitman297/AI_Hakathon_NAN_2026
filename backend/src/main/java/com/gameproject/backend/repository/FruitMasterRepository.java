package com.gameproject.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.FruitMaster;

public interface FruitMasterRepository extends JpaRepository<FruitMaster, Long> {
}
