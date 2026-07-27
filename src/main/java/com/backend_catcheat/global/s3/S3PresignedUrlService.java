package com.backend_catcheat.global.s3;


import com.backend_catcheat.domain.upload.dto.PresignedUploadRequestDTO;
import com.backend_catcheat.domain.upload.dto.PresignedUploadResponseDTO;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3PresignedUrlService {

    private static final int MAX_FILE_COUNT = 5;
    private static final Duration SIGNATURE_DURATION = Duration.ofMinutes(10);
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/heic",
            "image/heif"
    );

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public PresignedUploadResponseDTO createUploadUrls(PresignedUploadRequestDTO request) {

        // 업로드시 예외처리
        if (request.files() == null || request.files().isEmpty()) {
            throw new CustomException(ErrorCode.UPLOAD_FILE_REQUIRED);
        }

        if (request.files().size() > MAX_FILE_COUNT) {
            throw new CustomException(ErrorCode.UPLOAD_FILE_COUNT_EXCEEDED);
        }


        // 파일 별 presigned PUT URL을 발급
        List<PresignedUploadResponseDTO.UploadTarget> uploads = request.files().stream()
                .map(this::createUploadTarget)
                .toList();

        return new PresignedUploadResponseDTO(uploads);
    }

    private PresignedUploadResponseDTO.UploadTarget createUploadTarget(PresignedUploadRequestDTO.FileInfo file) {
        validateFile(file);

        // 원본 파일명을 UUID 기반 key로 변경 후 사용
        String key = generateKey(file.contentType());

        // presigned URL에 contentType을 포함하면, 실제 PUT 요청도 같은 Content-Type으로 보내야 한다.
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(key)
                .contentType(file.contentType())
                .build();

        // URL 유효시간을 짧게 제한하여 설정
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(SIGNATURE_DURATION)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return new PresignedUploadResponseDTO.UploadTarget(
                key,
                presignedRequest.url().toString(),
                buildPublicUrl(key)
        );
    }

    private void validateFile(PresignedUploadRequestDTO.FileInfo file) {
        // 클라이언트 검증은 우회될 수 있으므로 서버에서도 파일 정보를 다시 확인한다.
        if (file == null) {
            throw new CustomException(ErrorCode.UPLOAD_FILE_INFO_REQUIRED);
        }

        if (file.fileName() == null || file.fileName().isBlank()) {
            throw new CustomException(ErrorCode.UPLOAD_FILE_NAME_REQUIRED);
        }

        if (file.contentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.contentType())) {
            throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
        }
    }

    private String generateKey(String contentType) {
        // 날짜 경로를 넣어 S3 콘솔에서 업로드 시점을 대략적으로 추적하기 쉽게 한다.
        String datePath = LocalDate.now().format(DATE_PATH_FORMATTER);
        return "uploads/" + datePath + "/" + UUID.randomUUID() + extensionOf(contentType);
    }

    private String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/heic" -> ".heic";
            case "image/heif" -> ".heif";
            default -> throw new CustomException(ErrorCode.INVALID_UPLOAD_FILE);
        };
    }

    private String buildPublicUrl(String key) {
        return s3Properties.publicBaseUrl().replaceAll("/+$", "") + "/" + key;
    }

}
