package com.backend_catcheat.global.jpa.entity;


import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.cglib.core.Local;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;

@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
@Getter
public class BaseEntity {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    protected long id;

    @CreatedDate
    private LocalDate createDate;

    @LastModifiedDate
    private LocalDate modifyDate;

}
