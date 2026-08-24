package com.backend_catcheat.global.s3;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Function;

/**
 * 조회용 presigned URL 캐시. 같은 object key를 반복해서 서명하지 않기 위한 것이다.
 *
 * ## 왜 필요한가
 *
 * {@link S3PresignedUrlService#createDownloadUrl}은 호출마다 SigV4 서명을 새로 만든다.
 * 부하테스트에서 잰 값은 **서명 1회당 약 1.94ms**였다. 로그잇 피드는 응답 1건에 서명이 60회
 * 돌아 약 117ms를 서명에만 썼고, VU 150에서 CPU가 74%까지 올라갔다.
 * (측정 기록: docs/로그잇-피드-부하테스트-측정기록.md)
 *
 * 그런데 같은 key에 대한 서명 결과는 유효시간 안에서 몇 번을 만들든 똑같이 쓸 수 있다.
 * 매번 다시 만들 이유가 없다.
 *
 * ## TTL은 반드시 서명 유효시간보다 짧아야 한다
 *
 * 캐시에서 꺼내 준 URL은 **남은 유효시간이 그만큼 줄어든 상태**로 클라이언트에게 간다.
 * TTL 5분 / 서명 유효시간 10분이면, 최악의 경우에도 클라이언트는 5분 남은 URL을 받는다.
 * 이미지를 내려받기에 충분한 여유다.
 *
 * TTL을 서명 유효시간에 가깝게 올리면 만료 직전의 URL이 나가서 느린 회선에서 이미지가 깨진다.
 * 반대로 서명 유효시간을 늘려 여유를 벌 수도 있지만, 그건 URL이 유출됐을 때의 노출 창을
 * 넓히는 일이라 10분으로 짧게 잡아 둔 의도를 뒤집는다. **TTL만 짧게 두는 쪽을 택했다.**
 *
 * ## 보안상 새로 열리는 것은 없다
 *
 * 캐시 때문에 서로 다른 사용자가 같은 URL 문자열을 받게 되지만, 그 URL은 원래 그 이미지를
 * 볼 권한이 있는 사용자에게만 나간다. 접근 가능한 범위는 그대로고 문자열이 같아질 뿐이다.
 *
 * ## 왜 Map이 아니라 Caffeine인가
 *
 * 캐시 키가 S3 object key라 **사진이 업로드된 만큼 영구히 늘어난다.** 재사용되는 키가 없다.
 * 만료 스탬프를 값에 넣어도 "다시 조회되지 않는 키"는 맵에서 영원히 안 지워진다 —
 * 크기 상한과 축출이 있어야 한다. 직접 만들면 Caffeine을 나쁘게 다시 구현하게 된다.
 */
@Component
public class PresignedDownloadUrlCache {

    /**
     * 서명 유효시간(S3PresignedUrlService.SIGNATURE_DURATION = 10분)의 절반.
     * 이 관계가 깨지면 만료된 URL이 나갈 수 있다 — 한쪽을 바꾸면 다른 쪽도 봐야 한다.
     */
    static final Duration TTL = Duration.ofMinutes(5);

    /** 엔트리 하나가 key + URL로 약 1.5KB. 상한 1만이면 약 15MB로 묶인다 */
    private static final int MAX_SIZE = 10_000;

    private final Cache<String, String> cache;

    public PresignedDownloadUrlCache(MeterRegistry meterRegistry) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_SIZE)
                .expireAfterWrite(TTL)
                // 적중률을 Prometheus로 내보내 개선 효과를 그래프로 확인한다.
                // 이게 없으면 "캐시를 넣었다"는 말만 남고 얼마나 먹혔는지 알 수 없다
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, this.cache, "s3.presigned.download");
    }

    /** 캐시에 있으면 그대로, 없으면 {@code signer}로 만들어 담고 돌려준다 */
    public String get(String objectKey, Function<String, String> signer) {
        return cache.get(objectKey, signer);
    }

    /**
     * 객체를 지웠으면 그 key의 URL도 버린다.
     * 안 버리면 이미 없는 객체를 가리키는 URL이 최대 TTL(5분) 동안 더 나갈 수 있다.
     */
    public void invalidate(String objectKey) {
        cache.invalidate(objectKey);
    }
}
