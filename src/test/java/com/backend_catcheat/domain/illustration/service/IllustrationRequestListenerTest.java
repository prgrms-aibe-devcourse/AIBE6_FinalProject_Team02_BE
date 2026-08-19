package com.backend_catcheat.domain.illustration.service;

import com.backend_catcheat.domain.illustration.entity.IllustrationStatus;
import com.backend_catcheat.domain.illustration.event.IllustrationRequestedEvent;
import com.backend_catcheat.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.concurrent.RejectedExecutionException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("일러스트 생성 요청 수신")
class IllustrationRequestListenerTest {

    private static final long JOB_ID = 7L;

    private IllustrationGenerator generator;
    private IllustrationJobWriter jobWriter;
    private IllustrationRequestListener listener;

    @BeforeEach
    void setUp() {
        generator = mock(IllustrationGenerator.class);
        jobWriter = mock(IllustrationJobWriter.class);
        listener = new IllustrationRequestListener(generator, jobWriter);
    }

    @Test
    @DisplayName("대기열이 가득 차면 실패로 내린다 — 놓치면 화면이 영원히 폴링한다")
    void 대기열이_가득_차면_실패로_내린다() {
        doThrow(new RejectedExecutionException()).when(generator).start(JOB_ID);

        listener.onRequested(new IllustrationRequestedEvent(JOB_ID));

        InOrder order = inOrder(generator, jobWriter);
        order.verify(generator).start(JOB_ID);
        order.verify(jobWriter).fail(
                JOB_ID, IllustrationStatus.FAILED, ErrorCode.ILLUSTRATION_BUSY.name());
    }

    @Test
    @DisplayName("정상 접수되면 상태를 건드리지 않는다 — 결과는 생성기가 쓴다")
    void 정상_접수는_상태를_건드리지_않는다() {
        listener.onRequested(new IllustrationRequestedEvent(JOB_ID));

        verify(generator).start(JOB_ID);
        verifyNoInteractions(jobWriter);
    }
}
