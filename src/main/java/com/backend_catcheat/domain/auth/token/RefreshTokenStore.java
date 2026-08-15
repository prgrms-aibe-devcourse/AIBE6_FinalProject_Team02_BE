package com.backend_catcheat.domain.auth.token;

import com.backend_catcheat.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "RT:";//저장할때 키앞에 붙이는 접두사

    private final StringRedisTemplate redisTemplate;   // Spring Boot data-redis가 자동 등록
    private final JwtTokenProvider jwtTokenProvider;   // TTL 값을 가져오기 위함

    /** 로그인 시: userId에 refresh token을 저장한다(기존 값이 있으면 덮어씀 = 마지막 로그인만 유효). */
    public void save(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                key(userId),
                refreshToken,
                Duration.ofMillis(jwtTokenProvider.getRefreshTokenExpireMs())
        );
    }
    // 원자적 회전(CAS): 저장값이 oldRefresh와 같을 때만 newRefresh로 교체(+TTL).
    // GET→비교→SET을 한 번에 처리 → 동시 재발급 경쟁(비원자적 회전)으로 인한 세션 드롭 방지.
    private static final RedisScript<Long> ROTATE_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "redis.call('set', KEYS[1], ARGV[2], 'PX', ARGV[3]); return 1 " +
                    "else return 0 end",
            Long.class);

    /**
     * refresh 회전(CAS). 저장값이 oldRefresh와 일치할 때만 newRefresh로 원자적으로 교체한다.
     * @return 교체 성공(= 이 요청이 회전의 승자) 여부. 저장값이 다르거나(탈취/이미 회전) 없으면 false.
     */
    public boolean rotate(Long userId, String oldRefresh, String newRefresh) {
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(key(userId)),
                oldRefresh,
                newRefresh,
                String.valueOf(jwtTokenProvider.getRefreshTokenExpireMs()));
        return result != null && result == 1L;
    }


    /** 로그아웃/탈퇴 시: 해당 유저의 refresh token을 삭제해 즉시 무효화한다. */
    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
