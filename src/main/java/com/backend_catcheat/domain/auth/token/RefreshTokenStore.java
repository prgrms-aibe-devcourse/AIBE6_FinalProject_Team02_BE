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
    // 1: 정상 승자, 2: 유예창 내 허용, -1: 탈취 감지(모두 삭제), 0: 만료 또는 기타 실패
    private static final RedisScript<Long> ROTATE_SCRIPT = RedisScript.of(
            "local rt_key = KEYS[1]; local prev_key = KEYS[2]; " +
                    "local old_rt = ARGV[1]; local new_rt = ARGV[2]; " +
                    "local ttl = ARGV[3]; local prev_ttl = ARGV[4]; " +
                    "local current_rt = redis.call('get', rt_key); " +

                    "if current_rt == old_rt then " +
                    // 정상 회전: prev 갱신(5초), rt 갱신
                    "redis.call('set', prev_key, old_rt, 'PX', prev_ttl); " +
                    "redis.call('set', rt_key, new_rt, 'PX', ttl); " +
                    "return 1; " +
                    "elseif current_rt ~= false then " +
                    // 현재값과 다름. prev 확인
                    "local prev_rt = redis.call('get', prev_key); " +
                    "if prev_rt == old_rt then " +
                    // 유예창 내 동시 요청(선의의 패자)
                    "return 2; " +
                    "else " +
                    // 둘 다 다름 = 탈취 의심 (모든 토큰 파기)
                    "redis.call('del', rt_key); " +
                    "redis.call('del', prev_key); " +
                    "return -1; " +
                    "end " +
                    "else " +
                    // 토큰 없음(만료)
                    "return 0; " +
                    "end",
            Long.class);

    /**
     * refresh 회전(CAS). 저장값이 oldRefresh와 일치할 때만 newRefresh로 원자적으로 교체한다.
     * @return 교체 성공(= 이 요청이 회전의 승자) 여부. 저장값이 다르거나(탈취/이미 회전) 없으면 false.
     */
    public int rotate(Long userId, String oldRefresh, String newRefresh) {
        String rtKey = key(userId);
        String prevKey = "RT:prev:" + userId; // 이전 토큰 저장 키
        long ttl = jwtTokenProvider.getRefreshTokenExpireMs();
        long prevTtl = 5000L; // 5초(5000ms) 유예 시간

        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(rtKey, prevKey),
                oldRefresh,
                newRefresh,
                String.valueOf(ttl),
                String.valueOf(prevTtl)
        );

        return result != null ? result.intValue() : 0;
    }


    /** 로그아웃/탈퇴 시: 해당 유저의 refresh token을 삭제해 즉시 무효화한다. */
    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
