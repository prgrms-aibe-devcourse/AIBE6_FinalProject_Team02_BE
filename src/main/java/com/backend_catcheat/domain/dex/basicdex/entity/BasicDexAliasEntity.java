package com.backend_catcheat.domain.dex.basicdex.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 도감 칸의 별칭(검색 동의어).
 *
 * BaseEntity를 상속하지 않는다 — basic_dex_alias는 마이그레이션으로만 바뀌는 마스터 데이터라
 * 생성·수정 시각 컬럼이 없고, 없는 컬럼을 매핑하면 ddl-auto=validate가 실패한다.
 *
 * BasicDexEntity와 @ManyToOne으로 묶지 않고 식별자만 들고 있다. 조회는 "전부 읽어 인덱스를 만든다"
 * 한 가지뿐이라 연관관계 탐색이 필요 없고, 기존 엔티티를 건드리지 않아도 된다.
 */
@Entity
@Table(name = "basic_dex_alias")
@Getter
public class BasicDexAliasEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "basic_dex_id", nullable = false)
    private Long basicDexId;

    @Column(name = "alias", nullable = false)
    private String alias;
}
