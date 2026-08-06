package com.backend_catcheat.domain.challenge.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "challenge_dex_slot")
public class ChallengeDexSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_dex_id", nullable = false)
    private Long challengeDexId;

    @Column(name = "food_name", nullable = false, length = 100)
    private String foodName;

    @Column(name = "place_name", length = 255)
    private String placeName;                   // 장소 지정 챌린지용(없으면 음식만)

    private Double lat;
    private Double lng;

    @Column(name = "slot_order", nullable = false)
    private int slotOrder;

    @Column(name = "image_key", length = 512)
    private String imageKey;                     // 개설자가 등록한 목표 음식 사진(S3 key). 미해금이면 흑백 표시

    @Builder
    private ChallengeDexSlot(Long challengeDexId, String foodName, String placeName,
                             Double lat, Double lng, int slotOrder, String imageKey) {
        this.challengeDexId = challengeDexId;
        this.foodName = foodName;
        this.placeName = placeName;
        this.lat = lat;
        this.lng = lng;
        this.slotOrder = slotOrder;
        this.imageKey = imageKey;
    }
}