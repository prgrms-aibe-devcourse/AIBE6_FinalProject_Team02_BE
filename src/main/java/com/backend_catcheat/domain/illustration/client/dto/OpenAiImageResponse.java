package com.backend_catcheat.domain.illustration.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// 이미지 API는 URL이 아니라 base64로 돌려준다
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiImageResponse(
        List<Item> data,
        Usage usage
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(@JsonProperty("b64_json") String b64Json) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens
    ) {
    }

    public String firstImageBase64() {
        return data == null || data.isEmpty() ? null : data.getFirst().b64Json();
    }
}
