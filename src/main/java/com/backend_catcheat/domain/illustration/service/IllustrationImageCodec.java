package com.backend_catcheat.domain.illustration.service;

import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

// 등록 플로우의 ImagePreprocessor를 쓰지 않는다. 그쪽은 흰 배경을 깔고 JPEG로 인코딩해
// 투명 배경을 흰 사각형으로 만든다
@Slf4j
@Component
public class IllustrationImageCodec {

    public byte[] shrinkPng(byte[] source, int maxEdgePx) {
        BufferedImage decoded = decode(source);

        int longEdge = Math.max(decoded.getWidth(), decoded.getHeight());
        if (longEdge <= maxEdgePx) {
            return source;
        }

        double scale = (double) maxEdgePx / longEdge;
        int width = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(decoded.getHeight() * scale));

        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // 배경을 깔지 않는다. 흰색을 채우면 투명 배경이 사라진다
            g.drawImage(decoded, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }

        byte[] encoded = encodePng(target);
        log.info("[일러스트] 결과 축소 {}x{} {}B -> {}x{} {}B",
                decoded.getWidth(), decoded.getHeight(), source.length, width, height, encoded.length);
        return encoded;
    }

    private BufferedImage decode(byte[] source) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
            if (image == null) {
                throw new CustomException(ErrorCode.IMAGE_DECODE_FAILED);
            }
            return image;
        } catch (IOException e) {
            throw new CustomException(ErrorCode.IMAGE_DECODE_FAILED);
        }
    }

    private byte[] encodePng(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, "png", out)) {
                throw new CustomException(ErrorCode.IMAGE_ENCODE_FAILED);
            }
        } catch (IOException e) {
            throw new CustomException(ErrorCode.IMAGE_ENCODE_FAILED);
        }
        return out.toByteArray();
    }
}
