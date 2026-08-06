package com.gameproject.backend.repository;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcLocationSchedule;

public interface NpcLocationScheduleRepository extends JpaRepository<NpcLocationSchedule, Long> {

    /**
     * NPC 낮 동선 배치표는 서버 기동 시 시딩된 후 절대 안 바뀐다 — CacheConfig 참고.
     * npc를 그대로 캐시 키로 쓰면(기본 SimpleKeyGenerator) Npc가 equals/hashCode를 오버라이드
     * 안 해서 매 요청마다 새로 불러온 인스턴스가 서로 다른 키로 취급돼 캐시가 항상 miss되므로,
     * npcId(Long)를 키로 명시한다.
     */
    @Cacheable(value = "npcLocationSchedules", key = "#npc.npcId")
    List<NpcLocationSchedule> findByNpc(Npc npc);
}
