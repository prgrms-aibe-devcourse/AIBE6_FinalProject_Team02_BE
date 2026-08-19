package com.backend_catcheat.domain.made.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "made_dex_record_like")
public class MadeDexRecordLike extends BaseEntity {

    @Column(name="record_id", nullable=false)
    private Long recordId;

    @Column(name="user_id", nullable=false)
    private Long userId;

    private MadeDexRecordLike(Long recordId, Long userId) {
        this.recordId = recordId;
        this.userId = userId;
    }

    public static MadeDexRecordLike of(Long recordId, Long userId) {
        return new MadeDexRecordLike(recordId, userId);
    }
}
