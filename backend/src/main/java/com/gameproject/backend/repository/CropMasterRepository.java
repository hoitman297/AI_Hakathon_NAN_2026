package com.gameproject.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.CropMaster;

public interface CropMasterRepository extends JpaRepository<CropMaster, Long> {
}
