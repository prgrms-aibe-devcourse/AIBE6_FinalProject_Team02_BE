package com.backend_catcheat.domain.registration.service;

import com.backend_catcheat.domain.registration.config.VisionProperties;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImagePreprocessor {

    private static final List<Float> QUALITY_FALLBACKS = List.of(0.6f, 0.45f, 0.3f);

    private final VisionProperties properties;
    private final HeicConverter heicConverter;

    public PreparedImage prepare(byte[] source, String name) {
        return prepare(source, name, properties.maxLongEdgePx());
    }

    // 일러스트 변환은 형태가 뭉개지면 안 되어 음식 판정보다 큰 값을 쓴다
    public PreparedImage prepare(byte[] source, String name, int maxLongEdgePx) {
        BufferedImage decoded = decode(source);
        BufferedImage resized = resize(decoded, maxLongEdgePx);
        byte[] encoded = encodeWithinLimit(resized, name);

        PreparedImage prepared = new PreparedImage(
                encoded, source.length, resized.getWidth(), resized.getHeight(), name);

        log.info("[등록] 사진 전처리 name={} {}x{} {}B -> {}x{} {}B (절감 {}%)",
                name, decoded.getWidth(), decoded.getHeight(), source.length,
                prepared.width(), prepared.height(), prepared.encodedBytes(),
                reductionPercent(source.length, prepared.encodedBytes()));

        return prepared;
    }

    /**
     * ImageIO는 heic/heif를 읽지 못하고 null을 돌려준다. 먼저 JPEG로 바꿔 같은 경로로 합류시킨다.
     *
     * 판별은 Content-Type이 아니라 바이트로 한다. Content-Type은 클라이언트가 presign 때
     * 정한 값이라, jpeg로 발급받고 HEIC를 올리면 로더의 형식 게이트를 그대로 통과한다.
     */
    private BufferedImage decode(byte[] source) {
        try {
            byte[] decodable = HeicConverter.isHeic(source) ? heicConverter.toJpeg(source) : source;
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(decodable));
            if (image == null) {
                throw new CustomException(ErrorCode.IMAGE_DECODE_FAILED);
            }
            // ImageIO는 EXIF 회전을 적용하지 않는다. 아이폰 세로 사진이 눕은 채로 넘어간다.
            // HEIC는 heif-convert가 이미 돌려서 내보내므로 여기서는 1로 읽혀 그대로 지난다
            return ExifOrientation.apply(image, ExifOrientation.of(decodable));
        } catch (IOException e) {
            throw new CustomException(ErrorCode.IMAGE_DECODE_FAILED);
        }
    }

    private BufferedImage resize(BufferedImage source, int maxLongEdge) {
        int longEdge = Math.max(source.getWidth(), source.getHeight());
        double scale = longEdge <= maxLongEdge ? 1.0 : (double) maxLongEdge / longEdge;

        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // 투명 PNG를 JPEG로 바꾸면 알파가 검게 죽으므로 흰색을 깔아 둔다
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private byte[] encodeWithinLimit(BufferedImage image, String name) {
        byte[] encoded = encodeJpeg(image, properties.jpegQuality());
        if (encoded.length <= properties.maxEncodedBytes()) {
            return encoded;
        }

        for (float quality : QUALITY_FALLBACKS) {
            encoded = encodeJpeg(image, quality);
            log.warn("[등록] 인코딩 상한 초과로 품질을 낮춥니다 name={} quality={} size={}B", name, quality, encoded.length);
            if (encoded.length <= properties.maxEncodedBytes()) {
                return encoded;
            }
        }

        throw new CustomException(ErrorCode.PHOTO_TOO_LARGE);
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new CustomException(ErrorCode.IMAGE_ENCODE_FAILED);
        }
        ImageWriter writer = writers.next();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream imageOut = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(imageOut);

            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.IMAGE_ENCODE_FAILED);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static long reductionPercent(long original, long encoded) {
        return original <= 0 ? 0 : Math.round((1.0 - (double) encoded / original) * 100);
    }
}
