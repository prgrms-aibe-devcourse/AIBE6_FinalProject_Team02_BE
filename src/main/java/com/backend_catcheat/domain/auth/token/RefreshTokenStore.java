package com.backend_catcheat.domain.auth.token;

import com.backend_catcheat.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * refresh token을 Redis에 저장/조회/삭제한다.
 *
 * 왜 서버(Redis)에 저장하는가:
 * - 재발급 요청이 오면 "이게 서버가 발급한 유효한 refresh token이 맞는지" 대조해야 한다.
 * - 로그아웃/탈퇴 시 해당 유저의 refresh token을 즉시 무효화(삭제)할 수 있어야 한다.
 * key 형식은 "RT:{userId}", value는 refresh token 문자열.
 * TTL(만료)을 refresh 만료 시간과 똑같이 걸어 자동으로 사라지게 한다.
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "RT:";

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

    /** 재발급 시: 저장된 refresh token을 꺼낸다. 없으면(만료/로그아웃) empty. */
    public Optional<String> find(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    /** 로그아웃/탈퇴 시: 해당 유저의 refresh token을 삭제해 즉시 무효화한다. */
    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
