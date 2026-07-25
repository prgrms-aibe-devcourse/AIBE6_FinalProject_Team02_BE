package com.backend_catcheat.spike.vision.service;

import com.backend_catcheat.spike.vision.config.VisionSpikeProperties;
import com.backend_catcheat.spike.vision.exception.VisionSpikeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("이미지 전처리")
class ImagePreprocessorTest {

    private ImagePreprocessor preprocessor;

    @BeforeEach
    void setUp() {
        preprocessor = new ImagePreprocessor(new VisionSpikeProperties(5, 1024, 0.8f, 3_500_000L, true));
    }

    @Test
    @DisplayName("긴 변이 상한을 넘으면 비율을 유지한 채 축소한다")
    void 긴_변을_기준으로_축소한다() throws IOException {
        MockMultipartFile file = jpeg("food.jpg", 4000, 3000);

        PreparedImage prepared = preprocessor.prepare(file);

        assertThat(prepared.width()).isEqualTo(1024);
        assertThat(prepared.height()).isEqualTo(768); // 4:3 비율 유지
    }

    @Test
    @DisplayName("세로 사진은 세로를 기준으로 축소한다")
    void 세로_사진도_긴_변_기준이다() throws IOException {
        PreparedImage prepared = preprocessor.prepare(jpeg("portrait.jpg", 1500, 3000));

        assertThat(prepared.height()).isEqualTo(1024);
        assertThat(prepared.width()).isEqualTo(512);
    }

    @Test
    @DisplayName("상한보다 작은 사진은 확대하지 않는다")
    void 작은_사진은_확대하지_않는다() throws IOException {
        PreparedImage prepared = preprocessor.prepare(jpeg("small.jpg", 640, 480));

        assertThat(prepared.width()).isEqualTo(640);
        assertThat(prepared.height()).isEqualTo(480);
    }

    @Test
    @DisplayName("리사이즈로 용량이 실제로 줄어든다 — AI 비용 절감의 근거")
    void 용량이_줄어든다() throws IOException {
        MockMultipartFile file = jpeg("large.jpg", 4000, 3000);

        PreparedImage prepared = preprocessor.prepare(file);

        assertThat(prepared.encodedBytes()).isLessThan(prepared.originalBytes());
        assertThat(prepared.originalBytes()).isEqualTo(file.getSize());
    }

    @Test
    @DisplayName("알파 채널이 있는 PNG도 JPEG로 변환한다")
    void 투명_PNG를_처리한다() throws IOException {
        BufferedImage argb = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(argb, "png", out);
        MockMultipartFile file = new MockMultipartFile("images", "t.png", "image/png", out.toByteArray());

        PreparedImage prepared = preprocessor.prepare(file);

        // JPEG는 알파를 표현할 수 없으므로 흰 배경으로 평탄화
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(prepared.data()));
        assertThat(decoded.getColorModel().hasAlpha()).isFalse();
        assertThat(decoded.getWidth()).isEqualTo(800);
    }

    @Test
    @DisplayName("지원하지 않는 형식은 거부한다 — HEIC는 스파이크 범위 밖")
    void HEIC는_거부한다() {
        MockMultipartFile file = new MockMultipartFile("images", "photo.heic", "image/heic", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> preprocessor.prepare(file))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "UNSUPPORTED_IMAGE_FORMAT");
    }

    @Test
    @DisplayName("빈 파일은 거부한다")
    void 빈_파일을_거부한다() {
        MockMultipartFile file = new MockMultipartFile("images", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> preprocessor.prepare(file))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "EMPTY_IMAGE");
    }

    @Test
    @DisplayName("품질을 낮춰도 상한을 못 맞추면 인코딩 전용 코드로 거부한다")
    void 인코딩_상한을_못_맞추면_거부한다() throws IOException {
        // 업로드 상한(IMAGE_UPLOAD_TOO_LARGE)과 구분되는 경로임을 고정한다
        ImagePreprocessor tightLimit =
                new ImagePreprocessor(new VisionSpikeProperties(5, 1024, 0.8f, 100L, true));

        assertThatThrownBy(() -> tightLimit.prepare(jpeg("large.jpg", 4000, 3000)))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "IMAGE_ENCODE_TOO_LARGE");
    }

    @Test
    @DisplayName("확장자만 이미지이고 내용이 깨졌으면 거부한다 — 클라이언트 검증을 믿지 않는다")
    void 깨진_이미지를_거부한다() {
        MockMultipartFile file = new MockMultipartFile("images", "fake.jpg", "image/jpeg", "not an image".getBytes());

        assertThatThrownBy(() -> preprocessor.prepare(file))
                .isInstanceOf(VisionSpikeException.class)
                .hasFieldOrPropertyWithValue("code", "IMAGE_DECODE_FAILED");
    }

    private static MockMultipartFile jpeg(String name, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        Graphics2D g = image.createGraphics();
        for (int i = 0; i < 400; i++) {
            g.setColor(new Color(random.nextInt(0xFFFFFF)));
            g.fillRect(random.nextInt(width), random.nextInt(height), width / 12, height / 12);
        }
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return new MockMultipartFile("images", name, "image/jpeg", out.toByteArray());
    }
}
