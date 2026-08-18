package com.backend_catcheat.domain.auth.token;

import com.backend_catcheat.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * refresh 회전 동시성 검증 & 측정 (단일 세션 = 한 기기 내 여러 탭).
 *
 * 무엇을 재는가:
 *  - 동일 refresh(R0)로 N개 요청이 "동시에" 재발급을 시도할 때, 회전에 성공한 "승자 수"를 센다.
 *  - 비원자적(변경 전, {@link #nonAtomicRotate}) : 승자가 여러 명 가능 → Redis·쿠키 불일치 재현.
 *  - 원자적 CAS(변경 후, {@link RefreshTokenStore#rotate})       : 승자 정확히 1명 → 정합성 유지.
 *
 * 세션별 슬롯(sid) 전환 반영: 키가 RT:{uid}:{sid} 이므로 고정 SID 하나로 "한 기기 내" 동시성을 잰다.
 *
 * 선행 조건:
 *  1) 로컬 Redis 실행(localhost:6379). 안 떠 있으면 이 테스트는 자동 skip 된다.
 *  2) RefreshTokenStore.rotate(userId, sessionId, old, new) 시그니처가 적용되어 있어야 컴파일된다.
 *
 * 실행:  ./gradlew test --tests "*RefreshRotationConcurrencyTest*"
 */
class RefreshRotationConcurrencyTest {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RefreshTokenStore store;

    private static final long UID = 999_999L;
    private static final String SID = "test-session";           // 한 기기(세션) 고정
    private static final String KEY = "RT:" + UID + ":" + SID;   // 세션 슬롯 키
    private static final String PREV_KEY = "RT:prev:" + UID + ":" + SID;
    private static final String R0 = "OLD-REFRESH-TOKEN";
    private static final int N = 32; // 동시 요청 수

    @BeforeEach
    void setup() {
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        boolean redisUp;
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            redisUp = true;
        } catch (Exception e) {
            redisUp = false;
        }
        assumeTrue(redisUp, "로컬 Redis(localhost:6379)가 실행 중이어야 이 테스트가 동작합니다.");

        var jwt = new JwtTokenProvider("test-secret-32bytes-minimum-abcdefgh", 1_800_000L, 1_209_600_000L);
        store = new RefreshTokenStore(redisTemplate, jwt);
    }

    @AfterEach
    void cleanup() {
        if (redisTemplate != null) {
            try { redisTemplate.delete(KEY); } catch (Exception ignored) {}
            try { redisTemplate.delete(PREV_KEY); } catch (Exception ignored) {}
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /** 변경 전(비원자적): get → (지연) → equals → set. 잠금 없음 → 경쟁 창 존재. */
    private boolean nonAtomicRotate(String oldRt, String newRt) {
        String cur = redisTemplate.opsForValue().get(KEY);
        try { Thread.sleep(3); } catch (InterruptedException ignored) {} // 경쟁 창 확대
        if (cur != null && cur.equals(oldRt)) {
            redisTemplate.opsForValue().set(KEY, newRt);
            return true;
        }
        return false;
    }

    /** N개 스레드를 동시에 발사해 회전 성공(승자) 수를 센다. */
    private int countWinners(boolean useCas) throws Exception {
        redisTemplate.opsForValue().set(KEY, R0); // 매 라운드 R0로 리셋
        redisTemplate.delete(PREV_KEY);
        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch ready = new CountDownLatch(N);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        try {
            for (int i = 0; i < N; i++) {
                final String newRt = "NEW-" + i;
                pool.submit(() -> {
                    ready.countDown();
                    try { go.await(); } catch (InterruptedException ignored) {}
                    boolean ok = useCas ? (store.rotate(UID, SID, R0, newRt) == 1) // 1=정상 회전 승자
                            : nonAtomicRotate(R0, newRt);
                    if (ok) winners.incrementAndGet();
                });
            }
            ready.await();
            go.countDown();
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        return winners.get();
    }

    @Test
    void cas_allows_exactly_one_winner() throws Exception {
        int nonAtomic = countWinners(false); // 변경 전
        int atomic = countWinners(true);     // 변경 후(CAS)

        System.out.println("[측정] 비원자적(변경 전) 승자 수 = " + nonAtomic + " / " + N);
        System.out.println("[측정] 원자적(CAS, 변경 후) 승자 수 = " + atomic + " / " + N);

        assertThat(atomic).isEqualTo(1);
    }
}
