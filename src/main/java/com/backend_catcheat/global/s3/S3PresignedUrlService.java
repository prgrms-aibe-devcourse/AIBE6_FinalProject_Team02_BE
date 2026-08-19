package com.backend_catcheat.global.s3;


import com.backend_catcheat.domain.upload.dto.PresignedUploadRequestDTO;
import com.backend_catcheat.domain.upload.dto.PresignedUploadResponseDTO;
import com.backend_catcheat.domain.upload.dto.UploadPurpose;
import com.backend_catcheat.domain.upload.service.UploadObjectService;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3PresignedUrlService {

    private static final Duration SIGNATURE_DURATION = Duration.ofMinutes(10);
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final UploadObjectService uploadObjectService;

    public String createDownloadUrl(String objectLocation) {
        if (objectLocation == null || objectLocation.isBlank()) {
            return null;
        }

        if (objectLocation.startsWith("http://") || objectLocation.startsWith("https://")) {
            return objectLocation;
        }

        String key = Normalizer.normalize(extractKey(objectLocation), Normalizer.Form.NFD);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(SIGNATURE_DURATION)
                .getObjectRequest(getObjectRequest)
                .build();
        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    /**
     * S3 객체 삭제. 지웠으면 true.
     * 실패를 삼키되 결과는 알려 준다 — 남아 있는 객체의 발급 기록까지 지우면 아무나 쓸 수 있게 된다.
     */
    public boolean deleteObject(String objectLocation) {
        if (objectLocation == null || objectLocation.isBlank()) {
            return false;
        }
        try {
            String key = extractKey(objectLocation);
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(key)
                    .build());
            return true;
        } catch (Exception e) {
            log.warn("S3 객체 삭제 실패: {}", objectLocation, e);
            return false;
        }
    }

    public PresignedUploadResponseDTO createUploadUrls(Long userId, PresignedUploadRequestDTO request,
                                                       UploadPurpose purpose) {

        // 업로드시 예외처리
        if (request.files() == null || request.files().isEmpty()) {
            throw new CustomException(ErrorCode.UPLOAD_FILE_REQUIRED);
        }

        if (request.files().size() > purpose.maxFileCount()) {
            throw new CustomException(ErrorCode.UPLOAD_FILE_COUNT_EXCEEDED);
        }


        // 파일 별 presigned PUT URL을 발급
        List<PresignedUploadResponseDTO.UploadTarget> uploads = request.files().stream()
                .map(file -> createUploadTarget(file, purpose))
                .toList();

        // 발급받은 사람을 남겨야 나중에 이 key를 쓸 자격을 볼 수 있다
        uploadObjectService.issued(
                userId,
                uploads.stream().map(PresignedUploadResponseDTO.UploadTarget::key).toList(),
                purpose);

        return new PresignedUploadResponseDTO(uploads);
    }

    private PresignedUploadResponseDTO.UploadTarget createUploadTarget(PresignedUploadRequestDTO.FileInfo file,
                                                                      UploadPurpose purpose) {
        validateFile(file, purpose);

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
                presignedRequest.url().toString()
        );
    }

    private void validateFile(PresignedUploadRequestDTO.FileInfo file, UploadPurpose purpose) {
        // 클라이언트 검증은 우회될 수 있으므로 서버에서도 파일 정보를 다시 확인한다.
        if (file == null) {
            throw new CustomException(ErrorCode.UPLOAD_FILE_INFO_REQUIRED);
        }

        if (file.fileName() == null || file.fileName().isBlank()) {
            throw new CustomException(ErrorCode.UPLOAD_FILE_NAME_REQUIRED);
        }

        // 허용 형식은 용도가 정한다. 서버가 읽어야 하는 용도는 디코딩되는 형식만 받는다
        if (!purpose.allows(file.contentType())) {
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

    private String extractKey(String objectLocation) {
        String s3Prefix = "s3://" + s3Properties.bucket() + "/";
        if (objectLocation.startsWith(s3Prefix)) {
            return objectLocation.substring(s3Prefix.length());
        }
        if (objectLocation.startsWith("s3://")) {
            int keyStart = objectLocation.indexOf('/', "s3://".length());
            return keyStart >= 0 ? objectLocation.substring(keyStart + 1) : objectLocation;
        }
        return objectLocation.replaceFirst("^/+", "");
    }

}
