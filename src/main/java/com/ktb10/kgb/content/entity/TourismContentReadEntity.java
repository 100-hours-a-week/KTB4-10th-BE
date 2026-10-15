package com.ktb10.kgb.content.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Spring Data 조회 Repository의 기준이 되는 읽기 전용 관광 콘텐츠 엔티티입니다. */
@Entity
@Immutable
@Table(name = "tourism_contents")
public class TourismContentReadEntity {

    @Id
    private Long id;

    protected TourismContentReadEntity() {
    }
}
