package com.backend_catcheat.global.s3;

import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

// 다른 업로드는 전부 presigned PUT이다. 서버가 만들어 낸 바이트는 presign을 줄 상대가 없다
@Slf4j
@Service
@RequiredArgsConstructor
public class S3ObjectWriter {

    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    // contentType은 조회 시 프리사인 URL로 그대로 내려간다
    public String put(String prefix, byte[] content, String contentType, String extension) {
        String key = "%s/%s/%s%s".formatted(
                prefix, LocalDate.now().format(DATE_PATH_FORMATTER), UUID.randomUUID(), extension);

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(s3Properties.bucket())
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (Exception e) {
            log.error("S3 업로드 실패 key={} size={}B", key, content.length, e);
            throw new CustomException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }

        log.info("S3 업로드 key={} {}B", key, content.length);
        return key;
    }
}
