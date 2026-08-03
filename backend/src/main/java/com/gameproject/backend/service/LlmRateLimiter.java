package com.gameproject.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 계정당 LLM 호출 빈도에 슬라이딩 윈도 제한을 건다. 버그나 프론트 재시도 루프로 대화 API가
 * 반복 호출돼도 Anthropic API 비용이 무제한으로 새어나가지 않도록 하는 안전장치 —
 * 정상적인 플레이 속도(체력 소모 등 게임 내 제약)보다 훨씬 여유 있게 잡아, 실제 플레이를
 * 방해하지 않으면서 폭주만 잡아낸다.
 * 단일 인스턴스 기준 인메모리 구현이라 서버가 여러 대로 늘어나면 별도 저장소(Redis 등)로
 * 옮겨야 하지만, 이 프로젝트는 단일 인스턴스라 이 정도로 충분하다.
 */
@Component
public class LlmRateLimiter {

    private final int maxCallsPerWindow;
    private final Duration window;
    private final Clock clock;
    private final Map<Long, Deque<Instant>> recentCalls = new ConcurrentHashMap<>();

    public LlmRateLimiter(
            @Value("${app.llm-rate-limit.max-calls:20}") int maxCallsPerWindow,
            @Value("${app.llm-rate-limit.window-seconds:60}") long windowSeconds,
            Clock clock) {
        this.maxCallsPerWindow = maxCallsPerWindow;
        this.window = Duration.ofSeconds(windowSeconds);
        this.clock = clock;
    }

    /** 이번 호출이 허용 범위 내면 기록하고 통과, 초과했으면 예외를 던진다. */
    public void checkAllowed(Long accountId) {
        Deque<Instant> timestamps = recentCalls.computeIfAbsent(accountId, id -> new ConcurrentLinkedDeque<>());
        Instant now = clock.instant();
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && Duration.between(timestamps.peekFirst(), now).compareTo(window) > 0) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxCallsPerWindow) {
                throw new LlmRateLimitExceededException(
                        "LLM 호출이 너무 잦습니다 (분당 " + maxCallsPerWindow + "회 제한). 잠시 후 다시 시도해주세요.");
            }
            timestamps.addLast(now);
        }
    }
}
