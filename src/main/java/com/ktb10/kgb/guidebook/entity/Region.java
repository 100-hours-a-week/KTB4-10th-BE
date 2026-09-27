package com.ktb10.kgb.guidebook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 가이드북이 참조하는 광역 지역 기준 정보입니다. */
@Entity
@Table(name = "regions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "administrative_code", length = 20, nullable = false, unique = true)
    private String administrativeCode;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    public static Region create(String administrativeCode, String name) {
        Region region = new Region();
        region.administrativeCode = Objects.requireNonNull(administrativeCode);
        region.name = Objects.requireNonNull(name);
        return region;
    }
}
