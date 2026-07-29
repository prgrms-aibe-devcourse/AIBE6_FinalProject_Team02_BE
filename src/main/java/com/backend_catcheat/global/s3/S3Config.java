package com.backend_catcheat.global.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@RequiredArgsConstructor
public class S3Config {
    private final S3Properties s3Properties;

    /**
     * 분석 사진을 AI에 보내기 전에 서버가 다시 내려받는다 (§5.2 서버 리사이즈 필수, §7 서버 재검증).
     * presigned PUT은 바이트가 서버를 거치지 않아, 이 경로가 없으면 형식·크기를 확인할 방법이 없다.
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(s3Properties.region()))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(s3Properties.region()))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    private AwsCredentialsProvider credentialsProvider() {
        if (isBlank(s3Properties.accessKeyId()) || isBlank(s3Properties.secretAccessKey())) {
            return DefaultCredentialsProvider.create();
        }
        if (isBlank(s3Properties.sessionToken())) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    s3Properties.accessKeyId(),
                    s3Properties.secretAccessKey()
            ));
        }
        return StaticCredentialsProvider.create(AwsSessionCredentials.create(
                s3Properties.accessKeyId(),
                s3Properties.secretAccessKey(),
                s3Properties.sessionToken()
        ));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
