package com.backend_catcheat.domain.challenge.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "review")
public class Review extends BaseEntity {

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false, length = 20)
    private ReviewType reviewType;

    @Column(name = "challenge_dex_id", nullable = false)
    private Long challengeDexId;

    @Column(name = "slot_id")      //챌린지 리뷰시 null
    private Long slotId;

    @Column(name = "content", length = 500)
    private String content;

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    private Review(
            Long reviewerId,
            ReviewType reviewType,
            Long challengeDexId,
            Long slotId,
            String content,
            Integer rating
    ){
        this.reviewerId = reviewerId;
        this.reviewType = reviewType;
        this.challengeDexId = challengeDexId;
        this.slotId = slotId;
        this.content = content;
        this.rating = rating;
    }

    public static Review food(
            Long reviewerId,
            Long challengeDexId,
            Long slotId,
            String content,
            Integer rating
    ){
        return new Review(
                reviewerId,
                ReviewType.FOOD,
                challengeDexId,
                slotId,
                content,
                rating
        );
    }

    public static Review challenge(
            Long reviewerId,
            Long challengeDexId,
            String content,
            Integer rating
    ){
        return new Review(
                reviewerId,
                ReviewType.CHALLENGE,
                challengeDexId,
                null,
                content,
                rating
        );
    }
    public void edit(String content, Integer rating){
        this.content = content;
        this.rating = rating;
    }
    public void increaseLike(){
        this.likeCount++;
    }
    public void decreaseLike(){
        if(this.likeCount > 0){
            this.likeCount--;
        }
    }
    public boolean isOwnedBy(Long userId){
        return this.reviewerId.equals(userId);
    }
}
