package com.backend_catcheat.domain.dex.basicdex.entity;

import com.backend_catcheat.domain.dex.basicdex.type.Category;
import com.backend_catcheat.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;


@Entity
@Table(name="basic_dex")
@Getter
public class BasicDexEntity extends BaseEntity {

    @Column(name="name", nullable=false, unique=true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name="category", nullable=false)
    private Category category;

    @Column(name="illustration_url")
    private String illustrationUrl;

}
