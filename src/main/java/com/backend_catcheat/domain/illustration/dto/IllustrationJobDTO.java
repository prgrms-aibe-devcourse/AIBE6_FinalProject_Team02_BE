package com.backend_catcheat.domain.illustration.dto;

import com.backend_catcheat.domain.illustration.entity.IllustrationJob;
import com.backend_catcheat.domain.illustration.entity.IllustrationStatus;

// previewUrl은 만료되는 프리사인 URL이다. 저장할 값은 imageKey다
public record IllustrationJobDTO(
        Long jobId,
        IllustrationStatus status,
        String imageKey,
        String previewUrl,
        int revisionDepth,
        boolean canRevise,
        String instructions,
        String failureCode
) {

    // 방치된 GENERATING을 걸러내려고 상태를 밖에서 받는다
    public static IllustrationJobDTO of(IllustrationJob job, IllustrationStatus status,
                                        String previewUrl, String failureCode) {
        return new IllustrationJobDTO(
                job.getId(),
                status,
                job.getResultImageKey(),
                previewUrl,
                job.getRevisionDepth(),
                job.canRevise(),
                job.getInstructions(),
                failureCode);
    }

    public static IllustrationJobDTO accepted(IllustrationJob job) {
        return of(job, job.getStatus(), null, job.getFailureCode());
    }
}
