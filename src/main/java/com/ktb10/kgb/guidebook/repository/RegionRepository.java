package com.ktb10.kgb.guidebook.repository;

import com.ktb10.kgb.guidebook.entity.Region;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionRepository extends JpaRepository<Region, Long> {

    @Query(
            value =
                    """
                    SELECT *
                    FROM regions
                    WHERE name = :name
                      AND region_level = 'PROVINCE'
                    """,
            nativeQuery = true)
    Optional<Region> findProvinceByName(@Param("name") String name);
}
