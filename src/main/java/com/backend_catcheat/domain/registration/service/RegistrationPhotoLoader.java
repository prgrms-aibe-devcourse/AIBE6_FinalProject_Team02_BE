package com.backend_catcheat.domain.registration.service;

import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationPhotoLoader {

    // S3PresignedUrlService의 목록과 같이 유지한다
    private static final Set<String> STORABLE_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/heic", "image/heif");

    /**
     * ImageIO가 직접 읽거나, ImagePreprocessor가 변환을 거쳐 읽을 수 있는 형식.
     * heic/heif는 heif-convert로 JPEG를 거친다.
     *
     * 일러스트 생성도 loadForAnalysis를 쓰므로 여기를 열면 그 경로도 함께 열린다.
     */
    private static final Set<String> DECODABLE_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/heic", "image/heif");

    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public byte[] loadForAnalysis(String key) {
        return load(key, DECODABLE_CONTENT_TYPES, ErrorCode.PHOTO_FORMAT_NOT_ANALYZABLE);
    }

    // 해시만 계산하므로 디코딩되지 않는 형식도 통과시킨다
    public byte[] loadForStorage(String key) {
        return load(key, STORABLE_CONTENT_TYPES, ErrorCode.INVALID_UPLOAD_FILE);
    }

    public String hash(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }

    private byte[] load(String key, Set<String> allowedContentTypes, ErrorCode formatError) {
        // presigned PUT은 바이트가 서버를 거치지 않아 업로드 시점에 형식·크기를 막을 수 없다
        HeadObjectResponse head = head(key);

        if (head.contentLength() > MAX_BYTES) {
            log.warn("[등록] 사진 용량 초과 key={} size={}B", key, head.contentLength());
            throw new CustomException(ErrorCode.PHOTO_TOO_LARGE);
        }
        // 클라이언트가 presign 때 정한 값이라 실제 내용과 다를 수 있다. 싼 사전 필터일 뿐
        // 형식 검증이 아니다 — 실제 판별은 ImagePreprocessor가 매직 넘버로 한다
        if (head.contentType() == null || !allowedContentTypes.contains(head.contentType().toLowerCase())) {
            log.warn("[등록] 사진 형식 미지원 key={} contentType={}", key, head.contentType());
            throw new CustomException(formatError);
        }

        long startedAt = System.nanoTime();
        byte[] content = s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(s3Properties.bucket()).key(key).build()).asByteArray();
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        log.info("[등록] 사진 다운로드 key={} {}B {}ms", key, head.contentLength(), elapsedMs);

        return content;
    }

    private HeadObjectResponse head(String key) {
        try {
            return s3Client.headObject(
                    HeadObjectRequest.builder().bucket(s3Properties.bucket()).key(key).build());
        } catch (NoSuchKeyException e) {
            throw new CustomException(ErrorCode.PHOTO_NOT_UPLOADED);
        }
    }
}
