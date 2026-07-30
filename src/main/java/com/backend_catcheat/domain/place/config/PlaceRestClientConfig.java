package com.backend_catcheat.domain.place.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class PlaceRestClientConfig {

    private static final String KAKAO_LOCAL_BASE_URL = "https://dapi.kakao.com";

    @Bean
    public RestClient kakaoLocalRestClient(PlaceProperties properties) {
        Duration timeout = Duration.ofMillis(properties.timeoutMs());

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(KAKAO_LOCAL_BASE_URL)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.apiKey())
                .build();
    }
}
