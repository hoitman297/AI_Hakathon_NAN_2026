package com.gameproject.backend.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * NPC/상점 아이템/작물/과일/NPC 낮 동선 배치표는 서버 기동 시 DataSeeder가 한 번 채워 넣은
 * 뒤로는 게임 중 절대 바뀌지 않는 정적 마스터 데이터인데, 캐싱 없이 요청마다(원격 DB라
 * 왕복마다 실제 네트워크 레이턴시 발생) 다시 조회하고 있었다 — 특히 NPC 목록 화면은 NPC
 * 수만큼 동선 배치표를 반복 조회하는 N+1 패턴이라 체감 지연이 컸다.
 *
 * <p>ConcurrentMapCacheManager는 만료/축출이 없는 단순 인메모리 캐시라 별도 인프라
 * (Redis 등) 없이 바로 쓸 수 있다 — 이 데이터들은 배포 후 재시작 전까지 절대 안 바뀌므로
 * 만료 정책이 필요 없다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "npcs", "shopItems", "shopItemsById", "shopItemsByCode", "crops", "cropsById", "fruits", "fruitsById",
                "npcLocationSchedules");
    }
}
