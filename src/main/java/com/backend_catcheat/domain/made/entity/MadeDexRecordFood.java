package com.backend_catcheat.domain.made.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 테이블에 감사 컬럼이 없어 BaseEntity를 상속하지 않는다
@Entity
@Table(name = "made_dex_record_food")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MadeDexRecordFood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    // basic_dex를 참조하지 않는 자유 입력이다
    @Column(name = "food_name", nullable = false, length = MadeDexRecord.FOOD_NAME_MAX)
    private String foodName;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private MadeDexRecordFood(Long recordId, String foodName, int sortOrder) {
        this.recordId = recordId;
        this.foodName = foodName;
        this.sortOrder = sortOrder;
    }

    public static MadeDexRecordFood of(Long recordId, String foodName, int sortOrder) {
        return new MadeDexRecordFood(recordId, foodName, sortOrder);
    }
}
