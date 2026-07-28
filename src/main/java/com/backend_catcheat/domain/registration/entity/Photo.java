package com.backend_catcheat.domain.registration.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 업로드된 사진 1장. url에는 S3 object key를 담는다 —
 * 버킷이 비공개라 조회는 그때그때 presigned GET으로 만든다(고정 URL을 저장하면 만료된다).
 */
@Entity
@Table(name = "photo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Photo extends BaseEntity {

    @Column(name = "registration_id", nullable = false)
    private Long registrationId;

    /** S3 object key */
    @Column(name = "url", nullable = false, length = 500)
    private String url;

    /** 내용 해시. 전역 유일 — 같은 사진으로 반복 등록하는 어뷰징을 막는다 */
    @Column(name = "hash", nullable = false, unique = true)
    private String hash;

    private Photo(Long registrationId, String url, String hash) {
        this.registrationId = registrationId;
        this.url = url;
        this.hash = hash;
    }

    public static Photo of(Long registrationId, String key, String hash) {
        return new Photo(registrationId, key, hash);
    }
}
