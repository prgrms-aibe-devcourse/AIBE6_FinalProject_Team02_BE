package com.backend_catcheat.domain.registration.service;

import com.backend_catcheat.domain.registration.config.HeicProperties;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * HEIC/HEIF를 JPEG로 바꾼다. `heif-convert`(libheif)를 자식 프로세스로 부른다.
 *
 * JVM에 쓸 만한 순수 Java HEIC 디코더가 없다 — TwelveMonkeys 지원 목록에도 HEIF는 없다.
 * 바이너리와 HEVC 디코더 플러그인은 런타임 이미지에 함께 설치한다(Dockerfile 참고).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeicConverter {

    /** ISOBMFF는 `[4바이트 박스 크기]["ftyp"][4바이트 브랜드]`로 시작한다 */
    private static final int FTYP_OFFSET = 4;
    private static final int BRAND_OFFSET = 8;
    private static final int HEADER_MIN_BYTES = 12;

    /**
     * 브랜드는 촬영 방식마다 다르다 — 한 장은 heic, 연사·라이브포토는 heix/hevc,
     * 범용 컨테이너로 저장된 것은 mif1이 온다. 하나만 보면 일부가 새어 나간다.
     */
    private static final Set<String> HEIF_BRANDS =
            Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1");

    /** 실패 원인은 출력 앞부분에 나온다. 전체를 실으면 로그가 읽기 어려워진다 */
    private static final int OUTPUT_LOG_LIMIT = 500;

    private final HeicProperties properties;

    /**
     * Content-Type이 아니라 바이트로 판별한다.
     * Content-Type은 클라이언트가 presign 때 정한 값이라 실제 내용과 다를 수 있다.
     */
    public static boolean isHeic(byte[] source) {
        if (source == null || source.length < HEADER_MIN_BYTES) {
            return false;
        }
        if (!"ftyp".equals(new String(source, FTYP_OFFSET, 4, StandardCharsets.US_ASCII))) {
            return false;
        }
        String brand = new String(source, BRAND_OFFSET, 4, StandardCharsets.US_ASCII);
        return HEIF_BRANDS.contains(brand.toLowerCase());
    }

    public byte[] toJpeg(byte[] source) {
        Path input = null;
        Path output = null;
        try {
            // heif-convert는 파일 경로만 받는다. 표준 입력으로는 넘길 수 없다
            input = Files.createTempFile("heic-in-", ".heic");
            output = Files.createTempFile("heic-out-", ".jpg");
            Files.write(input, source);

            run(input, output);

            byte[] converted = Files.readAllBytes(output);
            if (converted.length == 0) {
                log.warn("[등록] HEIC 변환 결과가 비어 있음 inputBytes={}", source.length);
                throw new CustomException(ErrorCode.IMAGE_DECODE_FAILED);
            }
            log.info("[등록] HEIC 변환 {}B -> {}B", source.length, converted.length);
            return converted;
        } catch (IOException e) {
            log.warn("[등록] HEIC 변환 중 입출력 실패", e);
            throw new CustomException(ErrorCode.IMAGE_DECODE_FAILED);
        } finally {
            delete(input);
            delete(output);
        }
    }

    /**
     * 셸을 거치지 않고 인자 배열로 넘긴다. 문자열을 셸에 맡기면 인자가 명령으로 해석될 수 있다.
     * 지금은 임시 파일명을 우리가 만들지만, 셸 경유를 습관으로 두면 인자가 늘 때 뚫린다.
     */
    private void run(Path input, Path output) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
                properties.binaryPath(),
                "-q", String.valueOf(properties.quality()),
                input.toString(),
                output.toString());
        // 진행 메시지가 stderr로도 나온다. 한 스트림으로 합쳐 읽어야 버퍼가 차지 않는다
        builder.redirectErrorStream(true);

        Process process = builder.start();
        String processOutput = readOutput(process);

        boolean finished;
        try {
            finished = process.waitFor(properties.timeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new CustomException(ErrorCode.IMAGE_DECODE_FAILED);
        }

        if (!finished) {
            process.destroyForcibly();
            log.warn("[등록] HEIC 변환 타임아웃 {}ms", properties.timeoutMs());
            throw new CustomException(ErrorCode.IMAGE_DECODE_FAILED);
        }
        if (process.exitValue() != 0) {
            log.warn("[등록] HEIC 변환 실패 exit={} output={}", process.exitValue(), processOutput);
            throw new CustomException(ErrorCode.IMAGE_DECODE_FAILED);
        }
    }

    /** 자식이 출력을 뱉는데 읽지 않으면 파이프 버퍼가 차서 서로 멈춘다 */
    private String readOutput(Process process) throws IOException {
        String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        return text.length() > OUTPUT_LOG_LIMIT ? text.substring(0, OUTPUT_LOG_LIMIT) : text;
    }

    private void delete(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("[등록] HEIC 임시 파일 삭제 실패 path={}", path, e);
        }
    }
}
