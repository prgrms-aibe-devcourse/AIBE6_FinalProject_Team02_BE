package com.backend_catcheat.domain.illustration.service;

import com.backend_catcheat.domain.illustration.config.IllustrationProperties;
import com.backend_catcheat.domain.illustration.dto.IllustrationCreateRequestDTO;
import com.backend_catcheat.domain.illustration.dto.IllustrationJobDTO;
import com.backend_catcheat.domain.illustration.dto.RevisionRequestDTO;
import com.backend_catcheat.domain.illustration.entity.IllustrationJob;
import com.backend_catcheat.domain.illustration.entity.IllustrationMode;
import com.backend_catcheat.domain.illustration.entity.IllustrationStatus;
import com.backend_catcheat.domain.illustration.entity.RevisionPreset;
import com.backend_catcheat.domain.illustration.event.IllustrationRequestedEvent;
import com.backend_catcheat.domain.illustration.repository.IllustrationJobRepository;
import com.backend_catcheat.domain.upload.dto.UploadPurpose;
import com.backend_catcheat.domain.upload.entity.UploadObject;
import com.backend_catcheat.domain.upload.service.UploadObjectService;
import com.backend_catcheat.global.config.TimeConfig;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IllustrationService {

    private static final int FREE_TEXT_MAX = 100;

    // 외부 호출 타임아웃을 넘겨도 안 끝난 작업은 스레드가 사라진 것으로 본다
    private static final Duration STALE_MARGIN = Duration.ofSeconds(30);

    private final IllustrationJobRepository illustrationJobRepository;
    private final UploadObjectService uploadObjectService;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final ApplicationEventPublisher eventPublisher;
    private final IllustrationProperties properties;
    private final Clock clock;

    @Transactional
    public IllustrationJobDTO create(Long userId, IllustrationCreateRequestDTO request) {
        validate(request);
        requireUnderDailyLimit(userId);

        if (request.mode() == IllustrationMode.STYLIZE) {
            // 남의 key를 알아내 자기 일러스트에 쓰는 것을 막는다
            uploadObjectService.requireUsableBy(
                    userId, Set.of(request.sourceImageKey()), UploadPurpose.ILLUSTRATION_SOURCE);
        }

        IllustrationJob job = illustrationJobRepository.save(IllustrationJob.create(
                userId, request.purpose(), request.mode(),
                request.sourceImageKey(), trimToNull(request.description())));

        // 커밋된 뒤에 생성이 시작된다. 지금 부르면 비동기 스레드가 아직 없는 행을 읽는다
        eventPublisher.publishEvent(new IllustrationRequestedEvent(job.getId()));

        return IllustrationJobDTO.accepted(job);
    }

    @Transactional
    public IllustrationJobDTO revise(Long userId, Long jobId, RevisionRequestDTO request) {
        IllustrationJob parent = findOwned(userId, jobId);

        if (parent.getStatus() != IllustrationStatus.DONE) {
            throw new CustomException(ErrorCode.ILLUSTRATION_NOT_READY);
        }
        if (!parent.canRevise()) {
            throw new CustomException(ErrorCode.ILLUSTRATION_REVISION_LIMIT);
        }
        requireUnderDailyLimit(userId);

        String instruction = mergeInstruction(request);
        if (instruction.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        IllustrationJob revision = illustrationJobRepository.save(parent.revise(instruction));
        eventPublisher.publishEvent(new IllustrationRequestedEvent(revision.getId()));

        return IllustrationJobDTO.accepted(revision);
    }

    public IllustrationJobDTO find(Long userId, Long jobId) {
        IllustrationJob job = findOwned(userId, jobId);

        if (isStale(job)) {
            log.warn("[일러스트] 방치된 작업 jobId={} createdAt={}", jobId, job.getCreatedAt());
            return IllustrationJobDTO.of(
                    job, IllustrationStatus.FAILED, null, ErrorCode.ILLUSTRATION_FAILED.name());
        }

        String previewUrl = job.getResultImageKey() == null
                ? null
                : s3PresignedUrlService.createDownloadUrl(job.getResultImageKey());
        return IllustrationJobDTO.of(job, job.getStatus(), previewUrl, job.getFailureCode());
    }

    /**
     * 생성 스레드가 사라져 GENERATING으로 굳은 작업인지 본다.
     * 행 자체는 그대로 두고 화면에만 실패로 보인다 — 회수는 별도 이슈다.
     */
    private boolean isStale(IllustrationJob job) {
        if (job.getStatus() != IllustrationStatus.GENERATING) {
            return false;
        }
        LocalDateTime deadline = job.getCreatedAt()
                .plus(Duration.ofMillis(properties.timeoutMs()))
                .plus(STALE_MARGIN);
        return LocalDateTime.now(clock).isAfter(deadline);
    }

    private IllustrationJob findOwned(Long userId, Long jobId) {
        IllustrationJob job = illustrationJobRepository.findById(jobId)
                .orElseThrow(() -> new CustomException(ErrorCode.ILLUSTRATION_NOT_FOUND));
        if (!job.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.ILLUSTRATION_FORBIDDEN);
        }
        return job;
    }

    private void validate(IllustrationCreateRequestDTO request) {
        if (request.purpose() == null || request.mode() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (lengthOf(request.description()) > IllustrationJob.DESCRIPTION_MAX) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (lengthOf(request.sourceImageKey()) > UploadObject.IMAGE_KEY_MAX) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        if (request.mode() == IllustrationMode.GENERATE) {
            // 다른 자리는 t2i가 여백을 무시하고 고유색을 덮어써 무엇인지 알아볼 수 없다
            if (!request.purpose().isBadge()) {
                throw new CustomException(ErrorCode.ILLUSTRATION_MODE_NOT_ALLOWED);
            }
            if (isBlank(request.description())) {
                throw new CustomException(ErrorCode.ILLUSTRATION_DESCRIPTION_REQUIRED);
            }
            return;
        }
        if (isBlank(request.sourceImageKey())) {
            throw new CustomException(ErrorCode.ILLUSTRATION_SOURCE_REQUIRED);
        }
    }

    // 실패한 건도 센다. 호출 비용은 실패해도 나간다
    private void requireUnderDailyLimit(Long userId) {
        long today = illustrationJobRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                userId, startOfServiceDay());

        if (today >= properties.dailyLimit()) {
            log.info("[일러스트] 일일 상한 도달 userId={} count={}", userId, today);
            throw new CustomException(ErrorCode.ILLUSTRATION_LIMIT);
        }
    }

    private String mergeInstruction(RevisionRequestDTO request) {
        List<RevisionPreset> presets = request.presets() == null ? List.of() : request.presets();

        StringBuilder merged = new StringBuilder();
        presets.stream().distinct().forEach(preset -> {
            if (!merged.isEmpty()) {
                merged.append(" ");
            }
            merged.append(preset.instruction());
        });

        if (!isBlank(request.freeText())) {
            if (!merged.isEmpty()) {
                merged.append(" ");
            }
            // 길어질수록 앞의 스타일 규칙이 묻힌다. 400을 내면 유저가 쓴 것을 통째로 잃어 자른다
            String freeText = request.freeText().strip();
            merged.append(freeText.length() > FREE_TEXT_MAX ? freeText.substring(0, FREE_TEXT_MAX) : freeText);
        }
        return merged.toString();
    }

    /**
     * 한국 기준 자정을, createdAt이 저장된 시간대로 옮긴 값.
     * createdAt은 JPA auditing이 서버 기본 시간대로 채우므로 KST 자정을 그대로 비교하면
     * UTC 서버에서 하루의 앞 9시간이 어제로 밀려 상한이 헐거워진다.
     */
    private LocalDateTime startOfServiceDay() {
        Instant startOfDay = LocalDate.now(clock.withZone(TimeConfig.SERVICE_ZONE))
                .atStartOfDay(TimeConfig.SERVICE_ZONE)
                .toInstant();
        return LocalDateTime.ofInstant(startOfDay, clock.getZone());
    }

    private static int lengthOf(String value) {
        return value == null ? 0 : value.strip().length();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
