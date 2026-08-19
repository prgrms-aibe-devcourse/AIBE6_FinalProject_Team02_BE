package com.backend_catcheat.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    public static final String ILLUSTRATION_EXECUTOR = "illustrationExecutor";

    // 한 건이 소스 바이트·BufferedImage·base64 응답까지 들고 있어 약 50MB를 쓴다.
    // 공용 풀에 섞으면 짧은 작업까지 같이 느려진다
    @Bean(name = ILLUSTRATION_EXECUTOR)
    public Executor illustrationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("illust-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 진행 중인 생성이 배포로 끊기면 GENERATING으로 영영 남는다.
        // 외부 호출 타임아웃(catcheat.illustration.timeout-ms)보다 짧으면 끊는 셈이 된다
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
