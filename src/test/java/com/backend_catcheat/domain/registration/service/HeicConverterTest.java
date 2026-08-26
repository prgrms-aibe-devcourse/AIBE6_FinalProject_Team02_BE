package com.backend_catcheat.domain.registration.service;

import com.backend_catcheat.domain.registration.config.HeicProperties;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HEIC 변환")
class HeicConverterTest {

    /** 컨테이너에 설치되는 경로. application.yml의 catcheat.heic.binary-path와 같아야 한다 */
    private static final String BINARY_PATH = "/usr/bin/heif-convert";

    private static final String FIXTURE = "/heic/sample.heic";

    private static byte[] fixture() throws IOException {
        try (InputStream in = HeicConverterTest.class.getResourceAsStream(FIXTURE)) {
            assertThat(in).as("픽스처 %s", FIXTURE).isNotNull();
            return in.readAllBytes();
        }
    }

    /** `[4바이트 박스 크기]["ftyp"][브랜드]` 형태만 갖춘 최소 헤더 */
    private static byte[] ftypHeader(String brand) {
        byte[] bytes = new byte[16];
        bytes[3] = 16;
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, bytes, 4, 4);
        System.arraycopy(brand.getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 4);
        return bytes;
    }

    @Nested
    @DisplayName("바이트로 형식을 판별한다")
    class Detect {

        @ParameterizedTest
        @ValueSource(strings = {"heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1"})
        @DisplayName("HEIF 계열 브랜드를 모두 알아본다 — 연사·라이브포토는 heic가 아닌 브랜드로 온다")
        void HEIF_브랜드를_알아본다(String brand) {
            assertThat(HeicConverter.isHeic(ftypHeader(brand))).isTrue();
        }

        @Test
        @DisplayName("실제 HEIC 파일을 알아본다")
        void 실제_파일을_알아본다() throws IOException {
            assertThat(HeicConverter.isHeic(fixture())).isTrue();
        }

        @Test
        @DisplayName("JPEG는 HEIC가 아니다")
        void JPEG는_아니다() {
            assertThat(HeicConverter.isHeic(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                    0, 0, 0, 0, 0, 0, 0, 0})).isFalse();
        }

        @Test
        @DisplayName("ftyp이지만 HEIF 계열이 아니면 걸러 낸다 — mp4도 같은 컨테이너를 쓴다")
        void 다른_ftyp_브랜드는_거른다() {
            assertThat(HeicConverter.isHeic(ftypHeader("mp42"))).isFalse();
        }

        @Test
        @DisplayName("헤더보다 짧거나 비어 있으면 읽지 않는다")
        void 짧은_입력은_읽지_않는다() {
            assertThat(HeicConverter.isHeic(null)).isFalse();
            assertThat(HeicConverter.isHeic(new byte[0])).isFalse();
            assertThat(HeicConverter.isHeic(new byte[]{0, 0, 0, 16, 'f', 't', 'y', 'p'})).isFalse();
        }
    }

    /**
     * 실제 heif-convert가 있어야 도는 테스트. 로컬에는 보통 없어서 기본 실행에서 제외한다.
     * 컨테이너 안에서 `./gradlew test -PincludeExternal`로 돌린다.
     */
    @Nested
    @Tag("external")
    @DisplayName("heif-convert로 변환한다")
    class Convert {

        private final HeicConverter converter =
                new HeicConverter(new HeicProperties(BINARY_PATH, 90, 10_000));

        @Test
        @DisplayName("HEIC를 ImageIO가 읽을 수 있는 JPEG로 바꾼다")
        void HEIC를_JPEG로_바꾼다() throws IOException {
            byte[] source = fixture();
            assertThat(ImageIO.read(new ByteArrayInputStream(source)))
                    .as("변환 전에는 ImageIO가 읽지 못한다")
                    .isNull();

            byte[] converted = converter.toJpeg(source);

            assertThat(ImageIO.read(new ByteArrayInputStream(converted)))
                    .isNotNull()
                    .satisfies(image -> {
                        assertThat(image.getWidth()).isEqualTo(512);
                        assertThat(image.getHeight()).isEqualTo(683);
                    });
        }

        @Test
        @DisplayName("HEIC가 아닌 바이트를 넘기면 변환 실패로 막는다")
        void 깨진_입력은_실패로_막는다() {
            assertThatThrownBy(() -> converter.toJpeg(ftypHeader("heic")))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_DECODE_FAILED);
        }

        @Test
        @DisplayName("바이너리가 없으면 변환 실패로 막는다 — 이미지에 빠졌을 때 조용히 넘어가면 안 된다")
        void 바이너리가_없으면_실패로_막는다() throws IOException {
            HeicConverter missing =
                    new HeicConverter(new HeicProperties("/usr/bin/heif-convert-not-here", 90, 10_000));

            assertThatThrownBy(() -> missing.toJpeg(fixture()))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_DECODE_FAILED);
        }
    }
}
