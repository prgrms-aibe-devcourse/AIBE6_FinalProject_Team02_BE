package com.backend_catcheat.domain.registration.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "photo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Photo extends BaseEntity {

    @Column(name = "registration_id", nullable = false)
    private Long registrationId;

    // 이름과 달리 S3 object key를 담는다. presigned URL을 저장하면 만료된다
    @Column(name = "url", nullable = false, length = 500)
    private String url;

    private Photo(Long registrationId, String url) {
        this.registrationId = registrationId;
        this.url = url;
    }

    public static Photo of(Long registrationId, String key) {
        return new Photo(registrationId, key);
    }
}
