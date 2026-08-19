package com.backend_catcheat.domain.illustration.service;

import com.backend_catcheat.domain.illustration.config.IllustrationProperties;
import com.backend_catcheat.domain.illustration.entity.IllustrationJob;
import com.backend_catcheat.domain.illustration.entity.IllustrationStatus;
import com.backend_catcheat.domain.illustration.repository.IllustrationJobRepository;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 생성 스레드가 사라져 GENERATING으로 굳은 작업을 실패로 내린다.
 * 배포로 실행이 끊기거나 프로세스가 죽으면 행만 남고 아무도 손대지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IllustrationJobSweeper {

    // 외부 호출 타임아웃을 넘기고도 이만큼 더 지났으면 살아 있을 리 없다
    private static final Duration MARGIN = Duration.ofMinutes(5);

    private final IllustrationJobRepository illustrationJobRepository;
    private final IllustrationProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "PT5M")
    @Transactional
    public void sweep() {
        // createdAt은 JPA auditing이 서버 기본 시간대로 채운다. 비교 기준을 여기 맞춘다
        LocalDateTime deadline = LocalDateTime.now(clock)
                .minus(Duration.ofMillis(properties.timeoutMs()))
                .minus(MARGIN);

        List<IllustrationJob> stale = illustrationJobRepository
                .findByStatusAndCreatedAtLessThan(IllustrationStatus.GENERATING, deadline);
        if (stale.isEmpty()) {
            return;
        }

        stale.forEach(job -> job.fail(IllustrationStatus.FAILED, ErrorCode.ILLUSTRATION_FAILED.name()));
        log.warn("[일러스트] 방치된 작업 {}건을 실패로 내렸습니다 ids={}",
                stale.size(), stale.stream().map(IllustrationJob::getId).toList());
    }
}
