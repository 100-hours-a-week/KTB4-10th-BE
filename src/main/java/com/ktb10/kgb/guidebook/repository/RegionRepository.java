package com.ktb10.kgb.guidebook.repository;

import com.ktb10.kgb.guidebook.entity.Region;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByName(String name);
}
