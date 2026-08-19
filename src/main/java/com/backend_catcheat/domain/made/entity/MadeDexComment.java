package com.backend_catcheat.domain.made.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "made_dex_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MadeDexComment extends BaseEntity {

    @Column(name="made_dex_record_id", nullable=false)
    private Long madeDexRecordId;

    @Column(name="author_id", nullable=false)
    private Long authorId;


    @Column(name="like_count", nullable=false)
    private int likeCount = 0;


    @Column(name="content", nullable=false, length=500)
    private String content;

    private MadeDexComment(Long madeDexRecordId, Long authorId, String content) {
        this.madeDexRecordId = madeDexRecordId;
        this.authorId = authorId;
        this.content = content;
    }

    public static MadeDexComment write(Long madeDexRecordId, Long authorId,String content) {
        return new MadeDexComment(madeDexRecordId, authorId,content);
    }

    public void increaseLike() {
        this.likeCount++;
    }

    public void decreaseLike() {
        if(this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void update(String content) {
        this.content = content;
    }

    public boolean isAuthor(Long userId) {
        return this.authorId.equals(userId);
    }

}
