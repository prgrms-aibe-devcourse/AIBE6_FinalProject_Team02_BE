package com.backend_catcheat.spike.vision.service;

import com.backend_catcheat.spike.vision.config.VisionSpikeProperties;
import com.backend_catcheat.spike.vision.exception.VisionSpikeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Component
public class ImagePreprocessor {

    private static final Logger log = LoggerFactory.getLogger(ImagePreprocessor.class);

    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

    private static final List<Float> QUALITY_FALLBACKS = List.of(0.6f, 0.45f, 0.3f);

    private final VisionSpikeProperties properties;

    public ImagePreprocessor(VisionSpikeProperties properties) {
        this.properties = properties;
    }

    public PreparedImage prepare(MultipartFile file) {
        validate(file);

        BufferedImage source = decode(file);
        BufferedImage resized = resize(source, properties.maxLongEdgePx());
        byte[] encoded = encodeWithinLimit(resized, file.getOriginalFilename());

        PreparedImage prepared = new PreparedImage(
                encoded,
                file.getSize(),
                resized.getWidth(),
                resized.getHeight(),
                file.getOriginalFilename()
        );

        log.info("[spike] 전처리 완료 name={} {}x{} {}B -> {}x{} {}B (절감 {}%)",
                prepared.originalName(),
                source.getWidth(), source.getHeight(), prepared.originalBytes(),
                prepared.width(), prepared.height(), prepared.encodedBytes(),
                reductionPercent(prepared.originalBytes(), prepared.encodedBytes()));

        return prepared;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new VisionSpikeException("EMPTY_IMAGE", "빈 사진이 포함되어 있어요");
        }
        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new VisionSpikeException("UNSUPPORTED_IMAGE_FORMAT",
                    "JPG 또는 PNG 사진만 올릴 수 있어요 (받은 형식: %s)".formatted(contentType));
        }
    }

    private BufferedImage decode(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new VisionSpikeException("IMAGE_DECODE_FAILED", "사진을 읽을 수 없어요");
            }
            return image;
        } catch (IOException e) {
            throw new VisionSpikeException("IMAGE_DECODE_FAILED", "사진을 읽을 수 없어요", e);
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
            log.warn("[spike] 인코딩 상한 초과로 품질을 낮춥니다 name={} quality={} size={}B", name, quality, encoded.length);
            if (encoded.length <= properties.maxEncodedBytes()) {
                return encoded;
            }
        }

        throw new VisionSpikeException("IMAGE_ENCODE_TOO_LARGE",
                "사진 용량이 너무 커서 분석할 수 없어요 (%dB)".formatted(encoded.length));
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new VisionSpikeException("IMAGE_ENCODE_FAILED", "사진을 변환할 수 없어요");
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
            throw new VisionSpikeException("IMAGE_ENCODE_FAILED", "사진을 변환할 수 없어요", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static long reductionPercent(long original, long encoded) {
        return original <= 0 ? 0 : Math.round((1.0 - (double) encoded / original) * 100);
    }
}
