package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class LlmRateLimiterTest {

    @Test
    void checkAllowed_underLimit_neverThrows() {
        LlmRateLimiter limiter = new LlmRateLimiter(3, 60, new MutableClock(Instant.parse("2026-01-01T00:00:00Z")));

        limiter.checkAllowed(1L);
        limiter.checkAllowed(1L);
        limiter.checkAllowed(1L);
        // 3회 제한에서 3회까지는 전부 통과해야 한다 (경계값).
    }

    @Test
    void checkAllowed_exceedsLimitWithinWindow_throws() {
        LlmRateLimiter limiter = new LlmRateLimiter(3, 60, new MutableClock(Instant.parse("2026-01-01T00:00:00Z")));
        limiter.checkAllowed(1L);
        limiter.checkAllowed(1L);
        limiter.checkAllowed(1L);

        assertThatThrownBy(() -> limiter.checkAllowed(1L))
                .isInstanceOf(LlmRateLimitExceededException.class);
    }

    @Test
    void checkAllowed_differentAccounts_areIndependent() {
        LlmRateLimiter limiter = new LlmRateLimiter(1, 60, new MutableClock(Instant.parse("2026-01-01T00:00:00Z")));

        limiter.checkAllowed(1L);
        limiter.checkAllowed(2L); // 계정이 다르므로 1번 계정의 한도와 무관하게 통과해야 함

        assertThatThrownBy(() -> limiter.checkAllowed(1L))
                .isInstanceOf(LlmRateLimitExceededException.class);
    }

    @Test
    void checkAllowed_afterWindowExpires_allowsAgain() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LlmRateLimiter limiter = new LlmRateLimiter(2, 10, clock);
        limiter.checkAllowed(1L);
        limiter.checkAllowed(1L);
        assertThatThrownBy(() -> limiter.checkAllowed(1L))
                .isInstanceOf(LlmRateLimitExceededException.class);

        clock.advance(Duration.ofSeconds(11)); // 윈도(10초)를 넘겨서 오래된 기록이 만료되게 함

        limiter.checkAllowed(1L); // 다시 허용돼야 함
    }

    /** 테스트에서 시간 흐름을 직접 제어하기 위한 가변 Clock. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
