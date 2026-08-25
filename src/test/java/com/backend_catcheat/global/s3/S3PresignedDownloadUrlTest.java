package com.backend_catcheat.global.s3;

import com.backend_catcheat.domain.upload.service.UploadObjectService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 조회용 presigned URL과 그 캐시.
 *
 * presign은 로컬 서명 계산이라 네트워크가 필요 없다 — 더미 자격증명으로 돌린다.
 */
@DisplayName("조회 presigned URL")
class S3PresignedDownloadUrlTest {

    private static final String BUCKET = "test-bucket";

    private S3PresignedUrlService service;
    private PresignedDownloadUrlCache cache;

    @BeforeEach
    void setUp() {
        S3Presigner presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy-access-key", "dummy-secret-key")))
                .build();

        cache = new PresignedDownloadUrlCache(new SimpleMeterRegistry());
        service = new S3PresignedUrlService(
                presigner, mock(S3Client.class),
                new S3Properties(BUCKET, "ap-northeast-2", "dummy-access-key", "dummy-secret-key", null),
                mock(UploadObjectService.class),
                cache);
    }

    @Test
    @DisplayName("키가 없으면 서명하지 않고 null을 돌려준다 — 프로필이 비어 있는 사용자")
    void 키가_없으면_null() {
        assertThat(service.createDownloadUrl(null)).isNull();
        assertThat(service.createDownloadUrl("   ")).isNull();
    }

    @Test
    @DisplayName("이미 http(s) 주소면 그대로 통과시킨다")
    void 완성된_주소는_그대로() {
        String url = "https://cdn.example.com/a.png";
        assertThat(service.createDownloadUrl(url)).isEqualTo(url);
    }

    @Test
    @DisplayName("서명된 GET 주소를 만든다")
    void 서명된_주소를_만든다() {
        assertThat(service.createDownloadUrl("uploads/2026/08/24/a.jpg"))
                .contains(BUCKET)
                .contains("X-Amz-Signature");
    }

    @Test
    @DisplayName("s3:// 표기와 평문 key는 같은 항목으로 모인다 — 표기가 갈려 적중률이 떨어지면 안 된다")
    void 표기가_달라도_같은_URL() {
        String plain = service.createDownloadUrl("uploads/2026/08/24/a.jpg");
        String s3Uri = service.createDownloadUrl("s3://" + BUCKET + "/uploads/2026/08/24/a.jpg");

        assertThat(s3Uri).isEqualTo(plain);
    }

    /**
     * 서명 결과를 문자열로 비교하는 것만으로는 캐시를 증명할 수 없다 —
     * X-Amz-Date가 초 단위라 캐시가 없어도 같은 초 안에서는 같은 값이 나온다.
     * 그래서 **서명 함수가 몇 번 불렸는지**를 직접 센다.
     */
    @Test
    @DisplayName("같은 key는 한 번만 서명한다")
    void 같은_key는_한번만_서명한다() {
        AtomicInteger signCount = new AtomicInteger();

        for (int i = 0; i < 10; i++) {
            cache.get("uploads/a.jpg", key -> {
                signCount.incrementAndGet();
                return "signed:" + key;
            });
        }

        assertThat(signCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 key는 각각 서명한다")
    void 다른_key는_각각_서명한다() {
        AtomicInteger signCount = new AtomicInteger();

        cache.get("uploads/a.jpg", key -> sign(signCount, key));
        cache.get("uploads/b.jpg", key -> sign(signCount, key));

        assertThat(signCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("객체를 지우면 캐시도 버린다 — 없는 객체를 가리키는 URL이 더 나가면 안 된다")
    void 삭제하면_캐시를_버린다() {
        AtomicInteger signCount = new AtomicInteger();
        String key = "uploads/2026/08/24/a.jpg";

        cache.get(key, k -> sign(signCount, k));
        service.deleteObject(key);
        cache.get(key, k -> sign(signCount, k));

        assertThat(signCount.get()).isEqualTo(2);
    }

    private String sign(AtomicInteger counter, String key) {
        counter.incrementAndGet();
        return "signed:" + key;
    }
}
