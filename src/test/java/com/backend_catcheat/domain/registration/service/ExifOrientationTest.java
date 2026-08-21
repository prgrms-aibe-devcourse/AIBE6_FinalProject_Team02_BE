package com.backend_catcheat.domain.registration.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EXIF 회전")
class ExifOrientationTest {

    private static byte[] resource(String path) throws IOException {
        try (InputStream in = ExifOrientationTest.class.getResourceAsStream(path)) {
            assertThat(in).as("픽스처 %s", path).isNotNull();
            return in.readAllBytes();
        }
    }

    @Nested
    @DisplayName("JPEG에서 값을 읽는다")
    class Parse {

        @Test
        @DisplayName("아이폰 세로 사진의 6을 읽는다")
        void 세로_사진의_6을_읽는다() throws IOException {
            assertThat(ExifOrientation.of(resource("/heic/rotated.jpg"))).isEqualTo(6);
        }

        @Test
        @DisplayName("EXIF가 없으면 회전 없음으로 본다")
        void EXIF가_없으면_1() throws IOException {
            assertThat(ExifOrientation.of(resource("/heic/upright.jpg"))).isEqualTo(ExifOrientation.NORMAL);
        }

        @Test
        @DisplayName("JPEG가 아니면 읽지 않는다 — HEIC 변환 결과에도 그대로 부를 수 있어야 한다")
        void JPEG가_아니면_1() throws IOException {
            assertThat(ExifOrientation.of(resource("/heic/sample.heic"))).isEqualTo(ExifOrientation.NORMAL);
        }

        @Test
        @DisplayName("깨지거나 잘린 입력에도 예외를 던지지 않는다")
        void 깨진_입력에도_1() {
            assertThat(ExifOrientation.of(null)).isEqualTo(ExifOrientation.NORMAL);
            assertThat(ExifOrientation.of(new byte[0])).isEqualTo(ExifOrientation.NORMAL);
            // SOI만 있고 뒤가 잘린 경우
            assertThat(ExifOrientation.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
                    .isEqualTo(ExifOrientation.NORMAL);
        }
    }

    @Nested
    @DisplayName("이미지를 실제로 돌린다")
    class Apply {

        /** 왼쪽 위 한 칸만 흰색인 4x2. 회전 뒤 그 칸이 어디로 갔는지로 방향을 가린다 */
        private BufferedImage marked() {
            BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, 4, 2);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 1, 1);
            g.dispose();
            return image;
        }

        @Test
        @DisplayName("1이면 그대로 둔다")
        void 회전_없음은_그대로() {
            BufferedImage source = marked();
            assertThat(ExifOrientation.apply(source, ExifOrientation.NORMAL)).isSameAs(source);
        }

        @ParameterizedTest
        @CsvSource({"5, 2, 4", "6, 2, 4", "7, 2, 4", "8, 2, 4", "2, 4, 2", "3, 4, 2", "4, 4, 2"})
        @DisplayName("5~8은 가로세로가 뒤바뀐다")
        void 가로세로가_바뀐다(int orientation, int expectedWidth, int expectedHeight) {
            BufferedImage rotated = ExifOrientation.apply(marked(), orientation);

            assertThat(rotated.getWidth()).isEqualTo(expectedWidth);
            assertThat(rotated.getHeight()).isEqualTo(expectedHeight);
        }

        @Test
        @DisplayName("6은 시계 방향 90도 — 왼쪽 위 표식이 오른쪽 위로 간다")
        void 시계방향_90도() {
            BufferedImage rotated = ExifOrientation.apply(marked(), 6);

            assertThat(rotated.getRGB(rotated.getWidth() - 1, 0)).isEqualTo(Color.WHITE.getRGB());
            assertThat(rotated.getRGB(0, 0)).isEqualTo(Color.BLACK.getRGB());
        }

        @Test
        @DisplayName("3은 180도 — 왼쪽 위 표식이 오른쪽 아래로 간다")
        void 백팔십도() {
            BufferedImage rotated = ExifOrientation.apply(marked(), 3);

            assertThat(rotated.getRGB(3, 1)).isEqualTo(Color.WHITE.getRGB());
            assertThat(rotated.getRGB(0, 0)).isEqualTo(Color.BLACK.getRGB());
        }

        @Test
        @DisplayName("8은 반시계 90도 — 왼쪽 위 표식이 왼쪽 아래로 간다")
        void 반시계_90도() {
            BufferedImage rotated = ExifOrientation.apply(marked(), 8);

            assertThat(rotated.getRGB(0, rotated.getHeight() - 1)).isEqualTo(Color.WHITE.getRGB());
        }

        @Test
        @DisplayName("정의에 없는 값은 그대로 둔다")
        void 범위_밖은_그대로() {
            BufferedImage source = marked();
            assertThat(ExifOrientation.apply(source, 0)).isSameAs(source);
            assertThat(ExifOrientation.apply(source, 9)).isSameAs(source);
        }
    }
}
