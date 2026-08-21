package com.backend_catcheat.domain.registration.service;

import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("등록 사진 로딩")
class RegistrationPhotoLoaderTest {

    private static final String KEY = "uploads/2026/08/19/a.heic";

    private S3Client s3Client;
    private RegistrationPhotoLoader loader;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        loader = new RegistrationPhotoLoader(
                s3Client, new S3Properties("bucket", "ap-northeast-2", "id", "secret", null));
    }

    private void givenObject(String contentType, long length) {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentType(contentType).contentLength(length).build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[]{1, 2, 3}));
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/heic", "image/heif"})
    @DisplayName("분석용 로딩도 HEIC를 통과시킨다 — ImagePreprocessor가 heif-convert로 JPEG를 거친다")
    void 분석용은_HEIC를_통과시킨다(String contentType) {
        givenObject(contentType, 1024);

        assertThat(loader.loadForAnalysis(KEY)).hasSize(3);
    }

    @Test
    @DisplayName("분석용 로딩은 디코딩할 수 없는 형식에 원인을 짚어 준다")
    void 분석용은_디코딩_불가_형식에_원인을_알려준다() {
        givenObject("image/gif", 1024);

        assertThatThrownBy(() -> loader.loadForAnalysis(KEY))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PHOTO_FORMAT_NOT_ANALYZABLE);
    }

    @Test
    @DisplayName("저장용 검사는 HEIC를 통과시킨다 — 디코딩하지 않고 보관만 한다")
    void 저장용은_HEIC를_통과시킨다() {
        givenObject("image/heic", 1024);

        assertThatCode(() -> loader.validateForStorage(KEY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("저장용 검사는 사진을 내려받지 않는다 — 해시를 뜰 일이 없어졌다")
    void 저장용은_내려받지_않는다() {
        givenObject("image/jpeg", 1024);

        loader.validateForStorage(KEY);

        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("아예 이미지가 아니면 형식 오류로 막는다")
    void 이미지가_아니면_막는다() {
        givenObject("application/pdf", 1024);

        assertThatThrownBy(() -> loader.validateForStorage(KEY))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_UPLOAD_FILE);
    }

    @Test
    @DisplayName("10MB를 넘으면 내려받기 전에 막는다")
    void 용량을_넘으면_막는다() {
        givenObject("image/jpeg", 11L * 1024 * 1024);

        assertThatThrownBy(() -> loader.loadForAnalysis(KEY))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PHOTO_TOO_LARGE);
    }
}
