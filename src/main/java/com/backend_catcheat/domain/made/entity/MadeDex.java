package com.backend_catcheat.domain.made.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name;

    @Column(name = "description", length = DESCRIPTION_MAX)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private Visibility visibility;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private MadeDex(Long ownerId, String name, String description, Visibility visibility) {
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.visibility = visibility;
        this.maxMembers = MAX_MEMBERS;
    }

    public static MadeDex open(Long ownerId, String name, String description, Visibility visibility) {
        return new MadeDex(ownerId, name, description, visibility);
    }
}
