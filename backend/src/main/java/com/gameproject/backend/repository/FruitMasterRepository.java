package com.gameproject.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.FruitMaster;

public interface FruitMasterRepository extends JpaRepository<FruitMaster, Long> {

    /** 과일 마스터 데이터는 서버 기동 시 시딩된 후 절대 안 바뀐다 — CacheConfig 참고. */
    @Override
    @Cacheable("fruits")
    List<FruitMaster> findAll();

    @Override
    @Cacheable("fruitsById")
    Optional<FruitMaster> findById(Long id);
}
