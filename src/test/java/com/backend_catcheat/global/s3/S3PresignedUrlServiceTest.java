package com.backend_catcheat.global.s3;

import com.backend_catcheat.domain.upload.dto.PresignedUploadRequestDTO;
import com.backend_catcheat.domain.upload.dto.UploadPurpose;
import com.backend_catcheat.domain.upload.dto.PresignedUploadRequestDTO.FileInfo;
import com.backend_catcheat.domain.upload.dto.PresignedUploadResponseDTO;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * presign은 로컬 서명 계산이라 네트워크가 필요 없다 — 더미 자격증명으로 돌린다.
 */
@DisplayName("업로드 presigned URL 발급")
class S3PresignedUrlServiceTest {

    private static final String BUCKET = "test-bucket";

    private S3PresignedUrlService service;

    @BeforeEach
    void setUp() {
        S3Presigner presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy-access-key", "dummy-secret-key")))
                .build();

        service = new S3PresignedUrlService(
                presigner, mock(S3Client.class),
                new S3Properties(BUCKET, "ap-northeast-2", "dummy-access-key", "dummy-secret-key", null));
    }

    private PresignedUploadResponseDTO.UploadTarget issueOne(String fileName, String contentType) {
        return service.createUploadUrls(
                new PresignedUploadRequestDTO(List.of(new FileInfo(fileName, contentType)), null)).uploads().getFirst();
    }

    @Test
    @DisplayName("key는 업로드 날짜 경로 + UUID로 만든다 — 원본 파일명을 그대로 쓰지 않는다")
    void key는_날짜경로와_UUID로_만든다() {
        var target = issueOne("내 사진.jpg", "image/jpeg");

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        assertThat(target.key())
                .startsWith("uploads/" + today + "/")
                .endsWith(".jpg")
                // 원본 파일명이 새어 나가면 한글·공백이 키에 들어가 서명·조회가 흔들린다
                .doesNotContain("내 사진");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "image/jpeg, .jpg",
            "image/png,  .png",
            "image/heic, .heic",
            "image/heif, .heif",
    })
    @DisplayName("허용 형식마다 확장자를 붙인다")
    void 형식별_확장자(String contentType, String extension) {
        assertThat(issueOne("photo", contentType).key()).endsWith(extension);
    }

    @Test
    @DisplayName("uploadUrl은 서명된 PUT 주소이고 버킷·키를 가리킨다")
    void uploadUrl은_서명된_주소다() {
        var target = issueOne("photo.jpg", "image/jpeg");

        assertThat(target.uploadUrl())
                .contains(BUCKET)
                .contains("X-Amz-Signature")
                // Content-Type이 서명에 묶여 있어 PUT도 같은 값으로 보내야 한다
                .contains("content-type");
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/gif", "image/webp", "application/pdf", "text/plain"})
    @DisplayName("허용하지 않는 형식은 거부한다 — 클라이언트 검증만 믿지 않는다 (§7)")
    void 허용하지_않는_형식은_거부한다(String contentType) {
        assertThatThrownBy(() -> issueOne("bad", contentType))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_UPLOAD_FILE);
    }

    @Test
    @DisplayName("파일 이름이 없으면 거부한다")
    void 파일_이름이_없으면_거부한다() {
        assertThatThrownBy(() -> issueOne("  ", "image/jpeg"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_FILE_NAME_REQUIRED);
    }

    @Test
    @DisplayName("용도를 안 보내면 5장까지")
    void 다섯장을_넘으면_거부한다() {
        List<FileInfo> six = IntStream.range(0, 6)
                .mapToObj(i -> new FileInfo("photo" + i + ".jpg", "image/jpeg"))
                .toList();

        assertThatThrownBy(() -> service.createUploadUrls(new PresignedUploadRequestDTO(six, null)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_FILE_COUNT_EXCEEDED);
    }

    @Test
    @DisplayName("로그잇 기록은 8장까지, 9장은 거부한다")
    void 로그잇은_여덟장까지_받는다() {
        List<FileInfo> eight = IntStream.range(0, 8)
                .mapToObj(i -> new FileInfo("photo" + i + ".jpg", "image/jpeg"))
                .toList();
        List<FileInfo> nine = IntStream.range(0, 9)
                .mapToObj(i -> new FileInfo("photo" + i + ".jpg", "image/jpeg"))
                .toList();

        assertThat(service.createUploadUrls(
                new PresignedUploadRequestDTO(eight, UploadPurpose.LOGIT_RECORD)).uploads()).hasSize(8);

        assertThatThrownBy(() -> service.createUploadUrls(
                new PresignedUploadRequestDTO(nine, UploadPurpose.LOGIT_RECORD)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_FILE_COUNT_EXCEEDED);
    }

    @Test
    @DisplayName("사진이 없으면 거부한다")
    void 사진이_없으면_거부한다() {
        assertThatThrownBy(() -> service.createUploadUrls(new PresignedUploadRequestDTO(List.of(), null)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_FILE_REQUIRED);
    }

    @Test
    @DisplayName("여러 장은 요청 순서대로, 서로 다른 key로 발급한다")
    void 여러장은_서로_다른_key를_받는다() {
        var uploads = service.createUploadUrls(new PresignedUploadRequestDTO(List.of(
                new FileInfo("a.jpg", "image/jpeg"),
                new FileInfo("b.png", "image/png"),
                new FileInfo("c.jpg", "image/jpeg")), null)).uploads();

        assertThat(uploads).hasSize(3);
        assertThat(uploads.get(1).key()).endsWith(".png");
        assertThat(uploads).extracting(PresignedUploadResponseDTO.UploadTarget::key).doesNotHaveDuplicates();
    }
}
