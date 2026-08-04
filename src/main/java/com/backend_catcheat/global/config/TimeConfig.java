package com.backend_catcheat.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    /** 만료 판정을 테스트에서 sleep 없이 검증하려고 시계를 주입 가능하게 둔다 */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
