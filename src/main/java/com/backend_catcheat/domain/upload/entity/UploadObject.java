package com.backend_catcheat.domain.upload.entity;

import com.backend_catcheat.domain.upload.dto.UploadPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * presign을 발급받은 사람. key 자체가 식별자라 BaseEntity를 쓰지 않는다.
 */
@Entity
@Table(name = "upload_object")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadObject {

    public static final int IMAGE_KEY_MAX = 512;

    @Id
    @Column(name = "image_key", length = IMAGE_KEY_MAX)
    private String imageKey;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private UploadPurpose purpose;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private UploadObject(String imageKey, Long uploaderId, UploadPurpose purpose, LocalDateTime createdAt) {
        this.imageKey = imageKey;
        this.uploaderId = uploaderId;
        this.purpose = purpose;
        this.createdAt = createdAt;
    }

    public static UploadObject issued(String imageKey, Long uploaderId, UploadPurpose purpose, LocalDateTime now) {
        return new UploadObject(imageKey, uploaderId, purpose, now);
    }

    public boolean uploadedBy(Long userId) {
        return uploaderId.equals(userId);
    }

    public boolean isFor(UploadPurpose purpose) {
        return this.purpose == purpose;
    }
}
