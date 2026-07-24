package com.backend_catcheat.global.jwt;

import com.backend_catcheat.domain.auth.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 서비스 자체 access/refresh JWT를 발급하고 검증한다.
 * 소셜 프로바이더가 준 토큰과는 별개로, 이후 우리 API 인가는 이 토큰으로만 한다.
 *
 * 값들은 application.yml의 jwt.* 설정에서 주입받는다.
 */
@Slf4j // Slf4j는 로그를 남기기 위한 라이브러리. @Slf4j를 붙이면 log.info(), log.error() 등 사용 가능
@Component
public class JwtTokenProvider {
    // 토큰 안에 담는 정보(claim)의 키 이름들
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTokenExpireMs;
    private final long refreshTokenExpireMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expire-ms}") long accessTokenExpireMs,
            @Value("${jwt.refresh-token-expire-ms}") long refreshTokenExpireMs
    ) {
        // 문자열 시크릿을 HMAC 서명용 키로 변환. (secret이 짧으면 여기서 예외 → 충분히 길게)
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpireMs = accessTokenExpireMs;
        this.refreshTokenExpireMs = refreshTokenExpireMs;
    }
    /** access token: userId(subject) + role + type=access, 30분 만료. API 요청 인증에 쓴다. */
    public String createAccessToken(Long userId, Role role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpireMs))
                .signWith(key)
                .compact();
    }
    /** refresh token: userId(subject) + type=refresh, 14일 만료. access 재발급에만 쓴다. */
    public String createRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpireMs))
                .signWith(key)
                .compact();
    }
    /**
     * 토큰의 서명·만료를 검증한다.
     * 실패 사유(만료/위조)는 로그로만 남기고 밖에는 true/false만 준다.
     * (보안상 "왜 실패했는지"를 클라이언트에 자세히 노출하지 않는다 — 비기능 요구사항)
     */
    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("만료된 토큰");
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("유효하지 않은 토큰");
            return false;
        }
    }

    /** 토큰에서 userId(subject)를 꺼낸다. */
    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    /** 토큰에서 role을 꺼낸다. */
    public Role getRole(String token) {
        return Role.valueOf(parse(token).get(CLAIM_ROLE, String.class));
    }

    /** 이 토큰이 refresh 타입인지 확인 (access 토큰으로 재발급 시도하는 걸 막기 위함). */
    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(parse(token).get(CLAIM_TYPE, String.class));
    }

    /** Redis TTL 설정 시 재사용하려고 노출. */
    public long getRefreshTokenExpireMs() {
        return refreshTokenExpireMs;
    }



    /** 서명 검증 후 payload(claims)를 꺼내는 공통 로직. 서명이 안 맞거나 만료면 예외. */
    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}
