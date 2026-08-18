package com.backend_catcheat.domain.auth.token;

import com.backend_catcheat.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 다기기 동시 세션 검증 (세션별 슬롯 RT:{uid}:{sid} 전환).
 *
 * 핵심 보장:
 *  1) 두 기기(sidA, sidB)가 동시에 로그인해도 서로의 슬롯을 덮어쓰지 않는다.
 *  2) 한 기기의 회전(rotate)이 다른 기기 슬롯에 영향을 주지 않는다.
 *  3) 로그아웃(delete)은 그 기기 세션만 닫고(현재+prev), 다른 기기는 유지된다.
 *  4) deleteAll 은 유저의 전 세션을 파기한다.
 *
 * 선행 조건: 로컬 Redis(localhost:6379). 안 떠 있으면 자동 skip.
 *           RefreshTokenStore 의 sid 시그니처(save/rotate/delete/deleteAll)가 적용돼 있어야 컴파일.
 * 실행:  ./gradlew test --tests "*MultiDeviceSessionTest*"
 */
class MultiDeviceSessionTest {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RefreshTokenStore store;

    private static final long UID = 888_888L;
    private static final String SID_A = "device-A";
    private static final String SID_B = "device-B";
    private static final String KEY_A = "RT:" + UID + ":" + SID_A;
    private static final String KEY_B = "RT:" + UID + ":" + SID_B;
    private static final String PREV_A = "RT:prev:" + UID + ":" + SID_A;
    private static final String PREV_B = "RT:prev:" + UID + ":" + SID_B;

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
        cleanupKeys();
    }

    @AfterEach
    void cleanup() {
        cleanupKeys();
        if (connectionFactory != null) connectionFactory.destroy();
    }

    private void cleanupKeys() {
        if (redisTemplate != null) {
            try { redisTemplate.delete(java.util.List.of(KEY_A, KEY_B, PREV_A, PREV_B)); } catch (Exception ignored) {}
        }
    }

    @Test
    void two_devices_login_without_overwriting_each_other() {
        // 기기 A, 기기 B가 각자 로그인 (서로 다른 sid)
        store.save(UID, SID_A, "tokenA0");
        store.save(UID, SID_B, "tokenB0");

        // 두 슬롯이 독립적으로 공존한다 (예전 단일 슬롯이면 B가 A를 덮어썼을 것)
        assertThat(redisTemplate.opsForValue().get(KEY_A)).isEqualTo("tokenA0");
        assertThat(redisTemplate.opsForValue().get(KEY_B)).isEqualTo("tokenB0");
    }

    @Test
    void rotating_one_device_does_not_touch_the_other() {
        store.save(UID, SID_A, "tokenA0");
        store.save(UID, SID_B, "tokenB0");

        // 기기 A만 회전 → 승자(1)
        int result = store.rotate(UID, SID_A, "tokenA0", "tokenA1");
        assertThat(result).isEqualTo(1);

        // A 슬롯은 새 토큰, B 슬롯은 그대로
        assertThat(redisTemplate.opsForValue().get(KEY_A)).isEqualTo("tokenA1");
        assertThat(redisTemplate.opsForValue().get(KEY_B)).isEqualTo("tokenB0");

        // 기기 B도 자기 토큰으로 정상 회전된다(간섭 없음)
        assertThat(store.rotate(UID, SID_B, "tokenB0", "tokenB1")).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get(KEY_B)).isEqualTo("tokenB1");
    }

    @Test
    void logout_closes_only_that_device() {
        store.save(UID, SID_A, "tokenA0");
        store.save(UID, SID_B, "tokenB0");
        // A에서 한 번 회전해 prev 키도 생기게 함
        store.rotate(UID, SID_A, "tokenA0", "tokenA1");
        assertThat(redisTemplate.hasKey(PREV_A)).isTrue();

        // 기기 A 로그아웃 → A의 현재/prev 모두 삭제, B는 유지
        store.delete(UID, SID_A);
        assertThat(redisTemplate.hasKey(KEY_A)).isFalse();
        assertThat(redisTemplate.hasKey(PREV_A)).isFalse();
        assertThat(redisTemplate.opsForValue().get(KEY_B)).isEqualTo("tokenB0");
    }

    @Test
    void delete_all_clears_every_device() {
        store.save(UID, SID_A, "tokenA0");
        store.save(UID, SID_B, "tokenB0");

        store.deleteAll(UID);

        assertThat(redisTemplate.hasKey(KEY_A)).isFalse();
        assertThat(redisTemplate.hasKey(KEY_B)).isFalse();
    }
}
