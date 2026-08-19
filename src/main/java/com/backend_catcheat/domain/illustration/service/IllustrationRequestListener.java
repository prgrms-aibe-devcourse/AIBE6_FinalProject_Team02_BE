package com.backend_catcheat.domain.illustration.service;

import com.backend_catcheat.domain.illustration.entity.IllustrationStatus;
import com.backend_catcheat.domain.illustration.event.IllustrationRequestedEvent;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.RejectedExecutionException;

// 커밋 전에 시작하면 비동기 스레드가 아직 없는 행을 읽는다.
// @Async를 이 클래스에 함께 두면 자기 호출이라 프록시를 타지 않아 리스너를 분리했다
@Slf4j
@Component
@RequiredArgsConstructor
public class IllustrationRequestListener {

    private final IllustrationGenerator illustrationGenerator;
    private final IllustrationJobWriter illustrationJobWriter;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(IllustrationRequestedEvent event) {
        try {
            illustrationGenerator.start(event.jobId());
        } catch (RejectedExecutionException e) {
            // 여기서 놓치면 행이 GENERATING으로 남아 화면이 영원히 폴링한다
            log.warn("[일러스트] 대기열이 가득 차 생성을 시작하지 못했습니다 jobId={}", event.jobId());
            illustrationJobWriter.fail(
                    event.jobId(), IllustrationStatus.FAILED, ErrorCode.ILLUSTRATION_BUSY.name());
        }
    }
}
