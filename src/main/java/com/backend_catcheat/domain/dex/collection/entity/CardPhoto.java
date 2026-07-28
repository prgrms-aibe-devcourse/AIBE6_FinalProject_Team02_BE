package com.backend_catcheat.domain.dex.collection.entity;

import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카드에 붙은 사진. 같은 사진이 여러 카드에 붙을 수 있다 —
 * 한 상 사진 1장으로 여러 칸을 해금하는 경우가 정상 동작이기 때문이다 (§5.2).
 */
@Entity
@Table(name = "card_photo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardPhoto extends BaseEntity {

    @Column(name = "collection_card_id", nullable = false)
    private Long collectionCardId;

    @Column(name = "photo_id", nullable = false)
    private Long photoId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private CardPhoto(Long collectionCardId, Long photoId, int sortOrder) {
        this.collectionCardId = collectionCardId;
        this.photoId = photoId;
        this.sortOrder = sortOrder;
    }

    public static CardPhoto of(Long collectionCardId, Long photoId, int sortOrder) {
        return new CardPhoto(collectionCardId, photoId, sortOrder);
    }
}
