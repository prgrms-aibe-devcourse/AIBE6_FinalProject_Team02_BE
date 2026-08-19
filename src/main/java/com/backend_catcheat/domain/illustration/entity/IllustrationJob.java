package com.backend_catcheat.domain.illustration.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "illustration_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IllustrationJob extends BaseEntity {

    public static final int MAX_REVISION_DEPTH = 3;
    public static final int DESCRIPTION_MAX = 200;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private IllustrationPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private IllustrationMode mode;

    @Column(name = "source_image_key", length = 512)
    private String sourceImageKey;

    @Column(name = "description", length = DESCRIPTION_MAX)
    private String description;

    @Column(name = "parent_job_id")
    private Long parentJobId;

    @Column(name = "revision_depth", nullable = false)
    private short revisionDepth;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IllustrationStatus status;

    @Column(name = "result_image_key", length = 512)
    private String resultImageKey;

    @Column(name = "failure_code", length = 40)
    private String failureCode;

    private IllustrationJob(Long userId, IllustrationPurpose purpose, IllustrationMode mode,
                            String sourceImageKey, String description,
                            Long parentJobId, short revisionDepth, String instructions) {
        this.userId = userId;
        this.purpose = purpose;
        this.mode = mode;
        this.sourceImageKey = sourceImageKey;
        this.description = description;
        this.parentJobId = parentJobId;
        this.revisionDepth = revisionDepth;
        this.instructions = instructions;
        this.status = IllustrationStatus.GENERATING;
    }

    public static IllustrationJob create(Long userId, IllustrationPurpose purpose, IllustrationMode mode,
                                         String sourceImageKey, String description) {
        return new IllustrationJob(userId, purpose, mode, sourceImageKey, description, null, (short) 0, null);
    }

    // 물려받는 것은 부모의 결과물이 아니라 부모의 원본 사진이다.
    // 생성물을 되먹이면 3~4세대에서 크레파스 질감이 무너진다
    public IllustrationJob revise(String addedInstruction) {
        String merged = instructions == null || instructions.isBlank()
                ? addedInstruction
                : instructions + "\n" + addedInstruction;

        return new IllustrationJob(userId, purpose, mode, sourceImageKey, description,
                getId(), (short) (revisionDepth + 1), merged);
    }

    public boolean isOwnedBy(Long candidate) {
        return userId.equals(candidate);
    }

    // 결과가 없는 작업은 수정할 대상이 없다. 상태를 안 보면 실패한 건에도 상한이 깎인다
    public boolean canRevise() {
        return status == IllustrationStatus.DONE && revisionDepth < MAX_REVISION_DEPTH;
    }

    public void succeed(String resultImageKey) {
        this.resultImageKey = resultImageKey;
        this.status = IllustrationStatus.DONE;
        this.failureCode = null;
    }

    public void fail(IllustrationStatus status, String failureCode) {
        this.status = status;
        this.failureCode = failureCode;
    }
}
