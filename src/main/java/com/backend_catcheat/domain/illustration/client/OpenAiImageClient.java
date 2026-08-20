package com.backend_catcheat.domain.illustration.client;

import com.backend_catcheat.domain.illustration.client.dto.OpenAiImageResponse;
import com.backend_catcheat.domain.illustration.config.IllustrationProperties;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class OpenAiImageClient {

    private static final String EDITS_PATH = "/v1/images/edits";
    private static final String GENERATIONS_PATH = "/v1/images/generations";

    private final RestClient restClient;
    private final IllustrationProperties properties;

    public OpenAiImageClient(@Qualifier("openAiImageRestClient") RestClient restClient,
                             IllustrationProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public byte[] stylize(byte[] source, String sourceFileName, MediaType contentType, String prompt) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("model", properties.model());
        builder.part("prompt", prompt);
        builder.part("size", squareSize());
        builder.part("quality", properties.quality());
        builder.part("background", "transparent");
        builder.part("output_format", "png");
        builder.part("n", "1");
        // 확장자와 Content-Type이 어긋나면 400이 난다. 추론에 맡기지 않고 둘 다 명시한다
        builder.part("image", new ByteArrayResource(source), contentType)
                .filename(sourceFileName);

        return call(EDITS_PATH, builder.build(), MediaType.MULTIPART_FORM_DATA, "i2i");
    }

    public byte[] generate(String prompt) {
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "prompt", prompt,
                "size", squareSize(),
                "quality", properties.quality(),
                "background", "transparent",
                "output_format", "png",
                "n", 1);

        return call(GENERATIONS_PATH, body, MediaType.APPLICATION_JSON, "t2i");
    }

    private byte[] call(String path, Object body, MediaType contentType, String label) {
        long startedAt = System.nanoTime();

        OpenAiImageResponse response = restClient.post()
                .uri(path)
                .contentType(contentType)
                .body(body)
                .exchange((request, clientResponse) -> {
                    HttpStatusCode status = clientResponse.getStatusCode();
                    if (status.isError()) {
                        throw toException(status, clientResponse.bodyTo(String.class), label);
                    }
                    return clientResponse.bodyTo(OpenAiImageResponse.class);
                });

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        String base64 = response == null ? null : response.firstImageBase64();
        if (base64 == null || base64.isBlank()) {
            log.warn("[일러스트] {} 응답에 이미지가 없습니다 {}ms", label, elapsedMs);
            throw new CustomException(ErrorCode.ILLUSTRATION_FAILED);
        }

        byte[] image = Base64.getDecoder().decode(base64);
        log.info("[일러스트] {} 생성 완료 {}ms {}B in={}tok out={}tok",
                label, elapsedMs, image.length,
                response.usage() == null ? null : response.usage().inputTokens(),
                response.usage() == null ? null : response.usage().outputTokens());

        return image;
    }

    // 모더레이션 거절 응답 형식을 확인하지 못해 코드로 분기하지 않는다.
    // 4xx는 같은 사진으로 재시도해도 같으므로 거절로 묶는다
    private CustomException toException(HttpStatusCode status, String body, String label) {
        log.warn("[일러스트] {} 호출 실패 status={} body={}", label, status, abbreviate(body));
        return status.is4xxClientError()
                ? new CustomException(ErrorCode.ILLUSTRATION_REJECTED)
                : new CustomException(ErrorCode.ILLUSTRATION_FAILED);
    }

    private String squareSize() {
        return properties.resultSizePx() + "x" + properties.resultSizePx();
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= 500 ? body : body.substring(0, 500) + "...";
    }
}
