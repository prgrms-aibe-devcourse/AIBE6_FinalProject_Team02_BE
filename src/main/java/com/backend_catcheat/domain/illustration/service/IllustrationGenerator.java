package com.backend_catcheat.domain.illustration.service;

import com.backend_catcheat.domain.illustration.client.OpenAiImageClient;
import com.backend_catcheat.domain.illustration.entity.IllustrationJob;
import com.backend_catcheat.domain.illustration.entity.IllustrationMode;
import com.backend_catcheat.domain.illustration.entity.IllustrationStatus;
import com.backend_catcheat.domain.illustration.repository.IllustrationJobRepository;
import com.backend_catcheat.domain.registration.service.PreparedImage;
import com.backend_catcheat.global.config.AsyncConfig;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class IllustrationGenerator {

    // 다시 시도해도 결과가 같은 실패. 화면에 재시도 버튼을 내면 상한만 태운다
    private static final Set<ErrorCode> PERMANENT_FAILURES = Set.of(
            ErrorCode.ILLUSTRATION_REJECTED,
            ErrorCode.INVALID_UPLOAD_FILE,
            ErrorCode.PHOTO_FORMAT_NOT_ANALYZABLE,
            ErrorCode.PHOTO_TOO_LARGE,
            ErrorCode.PHOTO_NOT_UPLOADED,
            ErrorCode.IMAGE_DECODE_FAILED);

    private final IllustrationJobRepository illustrationJobRepository;
    private final IllustrationJobWriter illustrationJobWriter;
    private final IllustrationSourceLoader sourceLoader;
    private final PromptComposer promptComposer;
    private final OpenAiImageClient openAiImageClient;
    private final IllustrationResultStore resultStore;

    @Async(AsyncConfig.ILLUSTRATION_EXECUTOR)
    public void start(Long jobId) {
        run(jobId);
    }

    void run(Long jobId) {
        IllustrationJob job = illustrationJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("[일러스트] 작업이 사라졌습니다 jobId={}", jobId);
            return;
        }

        long startedAt = System.nanoTime();
        try {
            String key = resultStore.store(generate(job));

            illustrationJobWriter.succeed(jobId, key);
            log.info("[일러스트] 완료 jobId={} purpose={} depth={} {}ms",
                    jobId, job.getPurpose(), job.getRevisionDepth(), elapsedMs(startedAt));

        } catch (CustomException e) {
            IllustrationStatus status = PERMANENT_FAILURES.contains(e.getErrorCode())
                    ? IllustrationStatus.REJECTED
                    : IllustrationStatus.FAILED;
            illustrationJobWriter.fail(jobId, status, e.getErrorCode().name());
            log.warn("[일러스트] 실패 jobId={} code={} {}ms", jobId, e.getErrorCode(), elapsedMs(startedAt));

        } catch (Exception e) {
            // 여기서 예외가 새어 나가면 작업이 GENERATING으로 영영 남는다
            illustrationJobWriter.fail(jobId, IllustrationStatus.FAILED, ErrorCode.ILLUSTRATION_FAILED.name());
            log.error("[일러스트] 예기치 못한 실패 jobId={} {}ms", jobId, elapsedMs(startedAt), e);
        }
    }

    private byte[] generate(IllustrationJob job) {
        String prompt = promptComposer.compose(job);

        if (job.getMode() == IllustrationMode.GENERATE) {
            return openAiImageClient.generate(prompt);
        }

        PreparedImage source = sourceLoader.load(job.getSourceImageKey());

        // 전처리 결과는 항상 JPEG다. 확장자와 Content-Type을 여기 맞춘다
        return openAiImageClient.stylize(source.data(), "source.jpg", MediaType.IMAGE_JPEG, prompt);
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
