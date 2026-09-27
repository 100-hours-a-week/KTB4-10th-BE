package com.ktb10.kgb.guidebook.repository;

import com.ktb10.kgb.guidebook.entity.ItineraryDay;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, Long> {

    List<ItineraryDay> findAllByGuidebookIdOrderByDayNumberAsc(Long guidebookId);
}
