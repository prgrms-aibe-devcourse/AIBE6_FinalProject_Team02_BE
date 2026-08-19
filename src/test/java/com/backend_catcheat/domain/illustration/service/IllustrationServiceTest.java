package com.backend_catcheat.domain.illustration.service;

import com.backend_catcheat.domain.illustration.config.IllustrationProperties;
import com.backend_catcheat.domain.illustration.dto.IllustrationCreateRequestDTO;
import com.backend_catcheat.domain.illustration.dto.IllustrationJobDTO;
import com.backend_catcheat.domain.illustration.dto.RevisionRequestDTO;
import com.backend_catcheat.domain.illustration.entity.IllustrationJob;
import com.backend_catcheat.domain.illustration.entity.IllustrationMode;
import com.backend_catcheat.domain.illustration.entity.IllustrationPurpose;
import com.backend_catcheat.domain.illustration.entity.IllustrationStatus;
import com.backend_catcheat.domain.illustration.entity.RevisionPreset;
import com.backend_catcheat.domain.illustration.repository.IllustrationJobRepository;
import com.backend_catcheat.domain.upload.service.UploadObjectService;
import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.s3.S3PresignedUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("일러스트 생성 서비스")
class IllustrationServiceTest {

    private static final long USER_ID = 1L;
    private static final long JOB_ID = 10L;
    private static final long TIMEOUT_MS = 60_000L;

    private IllustrationJobRepository jobRepository;
    private ApplicationEventPublisher eventPublisher;
    private IllustrationService service;

    @BeforeEach
    void setUp() {
        jobRepository = mock(IllustrationJobRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        IllustrationProperties properties = new IllustrationProperties(
                "key", "gpt-image-1-mini", "medium", 1024, 1024, 512, 20, TIMEOUT_MS);

        service = new IllustrationService(
                jobRepository,
                mock(UploadObjectService.class),
                mock(S3PresignedUrlService.class),
                eventPublisher,
                properties,
                Clock.systemDefaultZone());
    }

    private IllustrationJob storedJob(IllustrationStatus status, int revisionDepth, LocalDateTime createdAt) {
        IllustrationJob job = IllustrationJob.create(
                USER_ID, IllustrationPurpose.CHALLENGE_SLOT, IllustrationMode.STYLIZE, "uploads/a.jpg", "김치찌개");
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        ReflectionTestUtils.setField(job, "status", status);
        ReflectionTestUtils.setField(job, "revisionDepth", (short) revisionDepth);
        ReflectionTestUtils.setField(job, "createdAt", createdAt);
        if (status == IllustrationStatus.DONE) {
            ReflectionTestUtils.setField(job, "resultImageKey", "illustrations/a.png");
        }
        return job;
    }

    private void given(IllustrationJob job) {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
    }

    @Test
    @DisplayName("사진 없이 STYLIZE를 요청하면 거부한다")
    void 사진이_없으면_거부한다() {
        var request = new IllustrationCreateRequestDTO(
                IllustrationPurpose.LOGIT_COVER, IllustrationMode.STYLIZE, null, null);

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ILLUSTRATION_SOURCE_REQUIRED);
    }

    @Test
    @DisplayName("뱃지가 아닌 자리는 설명만으로 만들 수 없다")
    void 뱃지가_아니면_GENERATE를_거부한다() {
        var request = new IllustrationCreateRequestDTO(
                IllustrationPurpose.CHALLENGE_SLOT, IllustrationMode.GENERATE, null, "매운맛 왕");

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ILLUSTRATION_MODE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("purpose나 mode가 비면 거부한다 — 통과시키면 DB 제약에서 500이 난다")
    void 필수값이_비면_거부한다() {
        var request = new IllustrationCreateRequestDTO(null, null, "uploads/a.jpg", null);

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("설명이 컬럼 길이를 넘으면 거부한다 — 통과시키면 DB 제약에서 500이 난다")
    void 설명이_너무_길면_거부한다() {
        var request = new IllustrationCreateRequestDTO(
                IllustrationPurpose.BADGE, IllustrationMode.GENERATE, null,
                "가".repeat(IllustrationJob.DESCRIPTION_MAX + 1));

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("일일 상한에 닿으면 작업을 만들지 않는다")
    void 일일_상한을_넘으면_거부한다() {
        when(jobRepository.countByUserIdAndCreatedAtGreaterThanEqual(anyLong(), any())).thenReturn(20L);

        var request = new IllustrationCreateRequestDTO(
                IllustrationPurpose.BADGE, IllustrationMode.GENERATE, null, "매운맛 왕");

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ILLUSTRATION_LIMIT);

        verify(jobRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("실패한 작업은 수정할 수 없다 — 되돌릴 결과가 없는데 상한만 깎인다")
    void 실패한_작업은_수정할_수_없다() {
        given(storedJob(IllustrationStatus.FAILED, 0, LocalDateTime.now()));

        assertThatThrownBy(() -> service.revise(USER_ID, JOB_ID, new RevisionRequestDTO(
                List.of(RevisionPreset.SIMPLER), null)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ILLUSTRATION_NOT_READY);

        verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("생성 중인 작업도 수정할 수 없다")
    void 생성_중인_작업은_수정할_수_없다() {
        given(storedJob(IllustrationStatus.GENERATING, 0, LocalDateTime.now()));

        assertThatThrownBy(() -> service.revise(USER_ID, JOB_ID, new RevisionRequestDTO(null, "더 크게")))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ILLUSTRATION_NOT_READY);
    }

    @Test
    @DisplayName("수정 3회를 채우면 더 못 고친다")
    void 수정_상한을_넘으면_거부한다() {
        given(storedJob(IllustrationStatus.DONE, IllustrationJob.MAX_REVISION_DEPTH, LocalDateTime.now()));

        assertThatThrownBy(() -> service.revise(USER_ID, JOB_ID, new RevisionRequestDTO(null, "더 크게")))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ILLUSTRATION_REVISION_LIMIT);
    }

    @Test
    @DisplayName("수정은 부모의 원본 사진을 물려받고 지시만 쌓는다")
    void 수정은_원본을_물려받는다() {
        given(storedJob(IllustrationStatus.DONE, 0, LocalDateTime.now()));
        when(jobRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.revise(USER_ID, JOB_ID, new RevisionRequestDTO(
                List.of(RevisionPreset.SIMPLER, RevisionPreset.BRIGHTER), "그릇을 빼줘"));

        var saved = org.mockito.ArgumentCaptor.forClass(IllustrationJob.class);
        verify(jobRepository).save(saved.capture());

        assertThat(saved.getValue().getSourceImageKey()).isEqualTo("uploads/a.jpg");
        assertThat(saved.getValue().getParentJobId()).isEqualTo(JOB_ID);
        assertThat(saved.getValue().getRevisionDepth()).isEqualTo((short) 1);
        assertThat(saved.getValue().getInstructions())
                .contains(RevisionPreset.SIMPLER.instruction())
                .contains(RevisionPreset.BRIGHTER.instruction())
                .contains("그릇을 빼줘");
    }

    @Test
    @DisplayName("남의 작업은 조회할 수 없다")
    void 남의_작업은_볼_수_없다() {
        given(storedJob(IllustrationStatus.DONE, 0, LocalDateTime.now()));

        assertThatThrownBy(() -> service.find(999L, JOB_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ILLUSTRATION_FORBIDDEN);
    }

    @Test
    @DisplayName("타임아웃을 한참 넘긴 GENERATING은 실패로 보여 준다 — 화면이 영원히 폴링하면 안 된다")
    void 방치된_작업은_실패로_보여준다() {
        LocalDateTime longAgo = LocalDateTime.now().minusSeconds(TIMEOUT_MS / 1000 + 600);
        given(storedJob(IllustrationStatus.GENERATING, 0, longAgo));

        IllustrationJobDTO found = service.find(USER_ID, JOB_ID);

        assertThat(found.status()).isEqualTo(IllustrationStatus.FAILED);
        assertThat(found.failureCode()).isEqualTo(ErrorCode.ILLUSTRATION_FAILED.name());
    }

    @Test
    @DisplayName("아직 시간이 남은 GENERATING은 그대로 둔다")
    void 진행_중인_작업은_그대로_둔다() {
        given(storedJob(IllustrationStatus.GENERATING, 0, LocalDateTime.now()));

        assertThat(service.find(USER_ID, JOB_ID).status()).isEqualTo(IllustrationStatus.GENERATING);
    }
}
