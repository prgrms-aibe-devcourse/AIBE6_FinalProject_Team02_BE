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

    /** objectPosition 백분율. 이 밖의 값은 화면에서 의미가 없다 */
    public static final double CROP_MIN = 0;
    public static final double CROP_MAX = 100;
    /** 안 고르면 가운데 */
    public static final double CROP_DEFAULT = 50;

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
    private double cropX = CROP_DEFAULT;

    @Column(name = "crop_y", nullable = false)
    private double cropY = CROP_DEFAULT;

    private MadeDexRecordPhoto(Long recordId, String imageKey, String caption, int sortOrder,
                               double cropX, double cropY) {
        this.recordId = recordId;
        this.imageKey = imageKey;
        this.caption = caption;
        this.sortOrder = sortOrder;
        this.cropX = clamp(cropX);
        this.cropY = clamp(cropY);
    }

    public static MadeDexRecordPhoto of(Long recordId, String imageKey, String caption, int sortOrder) {
        return new MadeDexRecordPhoto(recordId, imageKey, caption, sortOrder, CROP_DEFAULT, CROP_DEFAULT);
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
        this.cropX = clamp(cropX);
        this.cropY = clamp(cropY);
    }

    /**
     * 범위 밖 값은 거절하지 않고 좁힌다.
     * 좌표는 사용자가 타이핑하는 값이 아니라 드래그 결과라, 벗어났다면 반올림·기기 오차지
     * 사용자의 잘못이 아니다. 400으로 되돌려 봐야 고칠 방법이 없다.
     * DB의 ck_made_dex_record_photo_crop_* 는 이 계산이 뚫렸을 때를 위한 그물이다.
     */
    private static double clamp(double value) {
        return Math.max(CROP_MIN, Math.min(CROP_MAX, value));
    }
}
