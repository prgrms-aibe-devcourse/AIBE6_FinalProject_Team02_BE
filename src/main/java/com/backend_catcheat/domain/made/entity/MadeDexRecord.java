package com.backend_catcheat.domain.made.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "made_dex_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MadeDexRecord extends BaseEntity {

    // AI에 보내지 않아 상한 근거가 비용이 아니라 화면·용량이다. 기본 도감의 5장과 다른 값
    public static final int MAX_PHOTOS = 8;
    public static final int MIN_PHOTOS = 1;

    @Column(name = "made_dex_id", nullable = false)
    private Long madeDexId;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "logged_on", nullable = false)
    private LocalDate loggedOn;

    // 사용자가 적었을 때만 채운다. 비어 있으면 화면에도 시각을 띄우지 않는다
    @Column(name = "logged_at")
    private LocalDateTime loggedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private MadeDexRecord(Long madeDexId, Long slotId, Long authorId, LocalDate loggedOn, LocalDateTime loggedAt) {
        this.madeDexId = madeDexId;
        this.slotId = slotId;
        this.authorId = authorId;
        this.loggedOn = loggedOn;
        this.loggedAt = loggedAt;
    }

    public static MadeDexRecord write(Long madeDexId, Long slotId, Long authorId, LocalDate loggedOn,
                                      LocalDateTime loggedAt) {
        return new MadeDexRecord(madeDexId, slotId, authorId, loggedOn, loggedAt);
    }

    public void update(Long slotId, LocalDate loggedOn, LocalDateTime loggedAt) {
        this.slotId = slotId;
        this.loggedOn = loggedOn;
        this.loggedAt = loggedAt;
    }

    // 사진 행은 지우지 않는다. 조회 경로가 모두 deleted_at으로 걸러진다
    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isAuthor(Long userId) {
        return this.authorId.equals(userId);
    }

    public boolean belongsTo(Long madeDexId) {
        return this.madeDexId.equals(madeDexId);
    }
}
