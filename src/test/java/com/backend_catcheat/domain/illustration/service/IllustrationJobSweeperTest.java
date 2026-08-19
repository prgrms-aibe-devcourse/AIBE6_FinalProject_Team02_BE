package com.backend_catcheat.domain.illustration.service;

import com.backend_catcheat.domain.illustration.config.IllustrationProperties;
import com.backend_catcheat.domain.illustration.entity.IllustrationJob;
import com.backend_catcheat.domain.illustration.entity.IllustrationMode;
import com.backend_catcheat.domain.illustration.entity.IllustrationPurpose;
import com.backend_catcheat.domain.illustration.entity.IllustrationStatus;
import com.backend_catcheat.domain.illustration.repository.IllustrationJobRepository;
import com.backend_catcheat.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("방치된 일러스트 작업 회수")
class IllustrationJobSweeperTest {

    private static final long TIMEOUT_MS = 60_000L;

    private IllustrationJobRepository jobRepository;
    private IllustrationJobSweeper sweeper;

    @BeforeEach
    void setUp() {
        jobRepository = mock(IllustrationJobRepository.class);
        IllustrationProperties properties = new IllustrationProperties(
                "key", "gpt-image-1-mini", "medium", 1024, 1024, 512, 20, TIMEOUT_MS);

        sweeper = new IllustrationJobSweeper(jobRepository, properties, Clock.systemDefaultZone());
    }

    private IllustrationJob generatingJob() {
        return IllustrationJob.create(
                1L, IllustrationPurpose.BADGE, IllustrationMode.GENERATE, null, "매운맛 왕");
    }

    @Test
    @DisplayName("타임아웃과 여유를 넘긴 작업만 실패로 내린다")
    void 방치된_작업을_실패로_내린다() {
        IllustrationJob stale = generatingJob();
        ReflectionTestUtils.setField(stale, "id", 7L);
        when(jobRepository.findByStatusAndCreatedAtLessThan(eq(IllustrationStatus.GENERATING), any()))
                .thenReturn(List.of(stale));

        sweeper.sweep();

        assertThat(stale.getStatus()).isEqualTo(IllustrationStatus.FAILED);
        assertThat(stale.getFailureCode()).isEqualTo(ErrorCode.ILLUSTRATION_FAILED.name());
    }

    @Test
    @DisplayName("조회 기준은 타임아웃보다 과거다 — 진행 중인 작업을 끌어오면 안 된다")
    void 조회_기준이_충분히_과거다() {
        when(jobRepository.findByStatusAndCreatedAtLessThan(any(), any())).thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now();
        sweeper.sweep();

        ArgumentCaptor<LocalDateTime> deadline = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jobRepository).findByStatusAndCreatedAtLessThan(
                eq(IllustrationStatus.GENERATING), deadline.capture());

        assertThat(deadline.getValue()).isBefore(before.minusSeconds(TIMEOUT_MS / 1000));
    }
}
