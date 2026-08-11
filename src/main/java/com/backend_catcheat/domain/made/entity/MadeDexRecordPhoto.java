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
    public static final int CAPTION_MAX = 100;

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

    // 사진마다 붙이는 한마디. 기록 전체 메모를 대신한다
    @Column(name = "caption", length = CAPTION_MAX)
    private String caption;

    @Column(name = "crop_x", nullable = false)
    private double cropX = 50;

    @Column(name = "crop_y", nullable = false)
    private double cropY = 50;

    private MadeDexRecordPhoto(Long recordId, String imageKey, String caption, int sortOrder,
                               double cropX, double cropY) {
        this.recordId = recordId;
        this.imageKey = imageKey;
        this.caption = caption;
        this.sortOrder = sortOrder;
        this.cropX = cropX;
        this.cropY = cropY;
    }

    public static MadeDexRecordPhoto of(Long recordId, String imageKey, String caption, int sortOrder) {
        return new MadeDexRecordPhoto(recordId, imageKey, caption, sortOrder, 50, 50);
    }

    public static MadeDexRecordPhoto of(Long recordId, String imageKey, String caption, int sortOrder,
                                        double cropX, double cropY) {
        return new MadeDexRecordPhoto(recordId, imageKey, caption, sortOrder, cropX, cropY);
    }

    public void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** 남겨 두는 사진도 글은 고칠 수 있다 */
    public void writeCaption(String caption) {
        this.caption = caption;
    }

    public void writeCrop(double cropX, double cropY) {
        this.cropX = cropX;
        this.cropY = cropY;
    }
}
