package com.backend_catcheat.domain.made.entity;

import com.backend_catcheat.global.exception.CustomException;
import com.backend_catcheat.global.exception.ErrorCode;
import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "made_dex")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MadeDex extends BaseEntity {

    public static final int MAX_MEMBERS = 12;
    public static final int NAME_MAX = 100;
    public static final int DESCRIPTION_MAX = 500;
    public static final int IMAGE_KEY_MAX = 512;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name;

    @Column(name = "description", length = DESCRIPTION_MAX)
    private String description;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    /** 표지 이미지 S3 object key. null이면 이미지 없음 */
    @Column(name = "image_key", length = IMAGE_KEY_MAX)
    private String imageKey;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private MadeDex(Long ownerId, String name, String description, String imageKey) {
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.imageKey = imageKey;
        this.maxMembers = MAX_MEMBERS;
    }

    public static MadeDex open(Long ownerId, String name, String description, String imageKey) {
        return new MadeDex(ownerId, name, description, imageKey);
    }

    public void update(String name, String description, String imageKey) {
        this.name = name;
        this.description = description;
        this.imageKey = imageKey;
    }

    // 멤버 행은 지우지 않는다. 조회 경로가 모두 deleted_at으로 걸러진다
    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    // member 테이블의 role과 함께 옮겨야 두 값이 갈라지지 않는다
    public void changeOwner(Long newOwnerId) {
        this.ownerId = newOwnerId;
    }

    public boolean isOwner(Long userId) {
        return this.ownerId.equals(userId);
    }

    public void requireOwner(Long userId) {
        if (!isOwner(userId)) {
            throw new CustomException(ErrorCode.MADE_DEX_NOT_OWNER);
        }
    }
}
