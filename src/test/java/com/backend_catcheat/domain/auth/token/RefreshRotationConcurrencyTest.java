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
 * refresh 회전 동시성 검증 & 측정.
 *
 * 무엇을 재는가:
 *  - 동일 refresh(R0)로 N개 요청이 "동시에" 재발급을 시도할 때, 회전에 성공한 "승자 수"를 센다.
 *  - 비원자적(변경 전, {@link #nonAtomicRotate}) : 승자가 여러 명 가능 → Redis·쿠키 불일치 재현.
 *  - 원자적 CAS(변경 후, {@link RefreshTokenStore#rotate})       : 승자 정확히 1명 → 정합성 유지.
 *
 * 선행 조건:
 *  1) 로컬 Redis 실행(localhost:6379). 안 떠 있으면 이 테스트는 자동 skip 된다.
 *     (CI/격리가 필요하면 아래 setup을 Testcontainers 로 바꾼다 — build.gradle.kts 에 이미 의존성 있음.)
 *  2) B 적용(RefreshTokenStore.rotate 메서드 존재)이 되어 있어야 컴파일·실행된다.
 *
 * 실행:  ./gradlew test --tests "*RefreshRotationConcurrencyTest*"
 * 결과:  콘솔의 "[측정] 비원자적 승자 수" / "원자적 승자 수 = 1" 을 기록한다.
 */
class RefreshRotationConcurrencyTest {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RefreshTokenStore store;

    private static final long UID = 999_999L;
    private static final String KEY = "RT:" + UID;
    private static final String R0 = "OLD-REFRESH-TOKEN";
    private static final int N = 32; // 동시 요청 수

    @BeforeEach
    void setup() {
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        // 로컬 Redis가 안 떠 있으면 실패 대신 skip (원인 헷갈림 방지).
        boolean redisUp;
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            redisUp = true;
        } catch (Exception e) {
            redisUp = false;
        }
        assumeTrue(redisUp, "로컬 Redis(localhost:6379)가 실행 중이어야 이 테스트가 동작합니다.");

        // secret은 HS256용 32바이트 이상이면 됨(테스트 전용). TTL 값은 테스트에서 의미 없음.
        var jwt = new JwtTokenProvider("test-secret-32bytes-minimum-abcdefgh", 1_800_000L, 1_209_600_000L);
        store = new RefreshTokenStore(redisTemplate, jwt);
    }

    @AfterEach
    void cleanup() {
        if (redisTemplate != null) {
            try { redisTemplate.delete(KEY); } catch (Exception ignored) {}
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /** 변경 전(비원자적): get → (지연) → equals → set. 잠금 없음 → 경쟁 창 존재. */
    private boolean nonAtomicRotate(String oldRt, String newRt) {
        String cur = redisTemplate.opsForValue().get(KEY);
        try { Thread.sleep(3); } catch (InterruptedException ignored) {} // 경쟁 창 확대(조회~저장 지연 흉내)
        if (cur != null && cur.equals(oldRt)) {
            redisTemplate.opsForValue().set(KEY, newRt);
            return true;
        }
        return false;
    }

    /** N개 스레드를 동시에 발사해 회전 성공(승자) 수를 센다. */
    private int countWinners(boolean useCas) throws Exception {
        redisTemplate.opsForValue().set(KEY, R0); // 매 라운드 R0로 리셋
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
                    boolean ok = useCas ? store.rotate(UID, R0, newRt)
                                        : nonAtomicRotate(R0, newRt);
                    if (ok) winners.incrementAndGet();
                });
            }
            ready.await();       // 모든 스레드 준비 완료까지 대기
            go.countDown();      // 동시 발사
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

        // 변경 후 핵심 보장: 동시에 몰려도 회전 승자는 정확히 1명.
        assertThat(atomic).isEqualTo(1);
        // 변경 전은 환경/타이밍에 따라 1~N. 보통 >1 로 나오며, 그 값이 곧 race 증거다(콘솔로 기록).
    }
}
