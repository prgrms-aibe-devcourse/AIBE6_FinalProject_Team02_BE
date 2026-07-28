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

/**
 * 업로드된 사진을 S3에서 다시 읽는다.
 *
 * presigned PUT은 바이트가 서버를 거치지 않아 형식·크기를 강제할 수 없다.
 * 먼저 HeadObject로 메타데이터만 확인한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationPhotoLoader {

    /** 업로드 자체는 허용하는 형식 (S3PresignedUrlService와 같은 목록) */
    private static final Set<String> STORABLE_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/heic", "image/heif");

    /** ImageIO가 실제로 디코딩할 수 있는 형식. */
    private static final Set<String> DECODABLE_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    /** AI에 보낼 분석 사진. 디코딩 가능한 형식만 통과시킨다 */
    public byte[] loadForAnalysis(String key) {
        return load(key, DECODABLE_CONTENT_TYPES);
    }

    /** 카드에 붙일 사진. 해시 계산용이라 디코딩할 필요는 없다 */
    public byte[] loadForStorage(String key) {
        return load(key, STORABLE_CONTENT_TYPES);
    }

    /** 같은 사진 재사용 차단의 기준값 */
    public String hash(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }

    private byte[] load(String key, Set<String> allowedContentTypes) {
        HeadObjectResponse head = head(key);

        if (head.contentLength() > MAX_BYTES) {
            log.warn("[등록] 사진 용량 초과 key={} size={}B", key, head.contentLength());
            throw new CustomException(ErrorCode.PHOTO_TOO_LARGE);
        }
        if (head.contentType() == null || !allowedContentTypes.contains(head.contentType().toLowerCase())) {
            log.warn("[등록] 사진 형식 미지원 key={} contentType={}", key, head.contentType());
            throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
        }

        long startedAt = System.nanoTime();
        byte[] content = s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(s3Properties.bucket()).key(key).build()).asByteArray();
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        // 이 지연이 커지면 원본 URL을 AI에 그대로 넘기는 쪽으로 전환할지 판단
        log.info("[등록] 사진 다운로드 key={} {}B {}ms", key, head.contentLength(), elapsedMs);

        return content;
    }

    private HeadObjectResponse head(String key) {
        try {
            return s3Client.headObject(
                    HeadObjectRequest.builder().bucket(s3Properties.bucket()).key(key).build());
        } catch (NoSuchKeyException e) {
            // 업로드가 끝나기 전에 넘어왔거나, 클라이언트가 없는 key를 보낸 경우
            throw new CustomException(ErrorCode.PHOTO_NOT_UPLOADED);
        }
    }
}
