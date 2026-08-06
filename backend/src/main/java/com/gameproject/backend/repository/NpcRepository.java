package com.gameproject.backend.repository;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.Npc;

public interface NpcRepository extends JpaRepository<Npc, Long> {

    /** NPC 마스터 데이터는 서버 기동 시 시딩된 후 절대 안 바뀐다 — CacheConfig 참고. */
    @Override
    @Cacheable("npcs")
    List<Npc> findAll();
}
