package com.backend_catcheat.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    // 배포 서버가 UTC라 "오늘"을 clock의 기본 시간대로 판정하면 한국의 오전이 어제가 된다
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** 만료 판정을 테스트에서 sleep 없이 검증하려고 시계를 주입 가능하게 둔다 */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
