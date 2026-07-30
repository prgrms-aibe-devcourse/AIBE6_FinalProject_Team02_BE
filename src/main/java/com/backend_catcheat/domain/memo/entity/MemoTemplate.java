package com.backend_catcheat.domain.memo.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 저장해 둔 메모 문구. 제목 없이 본문만 두고 목록에 그대로 보여준다 */
@Entity
@Table(name = "memo_template")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoTemplate extends BaseEntity {

    /** 메모와 같은 상한 100자 */
    public static final int CONTENT_MAX = 100;

    // 이 값을 바꾸면 ErrorCode.MEMO_TEMPLATE_LIMIT_EXCEEDED 문구도 같이 고쳐야 한다
    public static final int MAX_COUNT = 3;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content", nullable = false, length = CONTENT_MAX)
    private String content;

    /** 목록 정렬 기준. 저장 시점에도 채워 두어 방금 만든 것이 맨 위에 온다 */
    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;

    private MemoTemplate(Long userId, String content, LocalDateTime lastUsedAt) {
        this.userId = userId;
        this.content = content;
        this.lastUsedAt = lastUsedAt;
    }

    public static MemoTemplate of(Long userId, String content, LocalDateTime now) {
        return new MemoTemplate(userId, content, now);
    }

    public void markUsed(LocalDateTime now) {
        this.lastUsedAt = now;
    }

    public boolean ownedBy(Long candidateUserId) {
        return userId.equals(candidateUserId);
    }
}
