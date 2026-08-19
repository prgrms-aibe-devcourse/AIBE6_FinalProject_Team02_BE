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
@Table(name = "made_dex_comment_like")
public class MadeDexCommentLike extends BaseEntity {

    @Column(name="comment_id", nullable=false)
    private Long commentId;

    @Column(name="user_id", nullable=false)
    private Long userId;

    private MadeDexCommentLike(Long commentId, Long userId) {
        this.commentId = commentId;
        this.userId = userId;
    }

    public static MadeDexCommentLike of(Long commentId, Long userId) {
        return new MadeDexCommentLike(commentId, userId);
    }
}
