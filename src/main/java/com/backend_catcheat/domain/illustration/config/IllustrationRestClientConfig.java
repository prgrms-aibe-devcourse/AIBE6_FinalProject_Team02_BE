package com.backend_catcheat.domain.illustration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

// Spring AI의 ImageModel은 t2i만 감싸고 있어 i2i·background·output_format을 실을 수 없다.
// 사진을 읽는 쪽(음식 판정)은 ChatModel을 그대로 쓴다
@Configuration
public class IllustrationRestClientConfig {

    private static final String OPENAI_BASE_URL = "https://api.openai.com";

    @Bean
    public RestClient openAiImageRestClient(IllustrationProperties properties) {
        Duration timeout = Duration.ofMillis(properties.timeoutMs());

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(OPENAI_BASE_URL)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
    }
}
