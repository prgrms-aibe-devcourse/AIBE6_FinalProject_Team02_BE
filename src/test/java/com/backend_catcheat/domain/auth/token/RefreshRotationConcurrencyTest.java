package com.backend_catcheat.domain.auth.token;

import com.backend_catcheat.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshRotationConcurrencyTest {

    StringRedisTemplate redisTemplate;
    RefreshTokenStore store;

    private static final long UID = 999_999L;
    private static final String KEY = "RT:" + UID;
    private static final String R0 = "OLD-REFRESH-TOKEN";

    @BeforeEach
    void setup() {
        var cf = new LettuceConnectionFactory("localhost", 6379);
        cf.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(cf);
        redisTemplate.afterPropertiesSet();
        // secret은 HS256용 32바이트 이상이면 됨(테스트 전용).
        var jwt = new JwtTokenProvider("test-secret-32bytes-minimum-abcdefgh", 1_800_000L, 1_209_600_000L);
        store = new RefreshTokenStore(redisTemplate, jwt);
    }

    // 변경 전(비원자적): get → (지연) → equals → set. 잠금 없음 → 경쟁.
    private boolean nonAtomicRotate(String oldRt, String newRt) {
        String cur = redisTemplate.opsForValue().get(KEY);
        try { Thread.sleep(3); } catch (InterruptedException ignored) {}  // 경쟁 창 확대
        if (cur != null && cur.equals(oldRt)) {
            redisTemplate.opsForValue().set(KEY, newRt);
            return true;
        }
        return false;
    }

    private int countWinners(boolean useCas) throws Exception {
        int N = 32;
        redisTemplate.opsForValue().set(KEY, R0);   // 매 라운드 R0로 리셋
        var pool = Executors.newFixedThreadPool(N);
        var ready = new CountDownLatch(N);
        var go = new CountDownLatch(1);
        var winners = new AtomicInteger();
        for (int i = 0; i < N; i++) {
            final String newRt = "NEW-" + i;
            pool.submit(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException ignored) {}
                boolean ok = useCas ? store.rotate(UID, R0, newRt)
                                    : nonAtomicRotate(R0, newRt);
                if (ok) winners.incrementAndGet();
            });
        }
        ready.await();
        go.countDown();                              // 동시 발사
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        return winners.get();
    }

    @Test
    void cas_allows_exactly_one_winner() throws Exception {
        int nonAtomic = countWinners(false);  // 변경 전
        int atomic = countWinners(true);      // 변경 후
        System.out.println("[측정] 비원자적(변경 전) 승자 수 = " + nonAtomic);
        System.out.println("[측정] 원자적(CAS, 변경 후) 승자 수 = " + atomic);
        assertThat(atomic).isEqualTo(1);      // 변경 후: 정확히 1 (회귀 방지)
        // nonAtomic은 보통 >1 → race 증거(콘솔 값 기록).
    }
}
