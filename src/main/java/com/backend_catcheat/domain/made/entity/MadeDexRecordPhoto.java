package com.backend_catcheat.domain.made.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 테이블에 감사 컬럼이 없어 BaseEntity를 상속하지 않는다
@Entity
@Table(name = "made_dex_record_photo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MadeDexRecordPhoto {

    public static final int IMAGE_KEY_MAX = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "image_key", nullable = false, length = IMAGE_KEY_MAX)
    private String imageKey;

    // 0번이 카드 썸네일
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private MadeDexRecordPhoto(Long recordId, String imageKey, int sortOrder) {
        this.recordId = recordId;
        this.imageKey = imageKey;
        this.sortOrder = sortOrder;
    }

    public static MadeDexRecordPhoto of(Long recordId, String imageKey, int sortOrder) {
        return new MadeDexRecordPhoto(recordId, imageKey, sortOrder);
    }
}
