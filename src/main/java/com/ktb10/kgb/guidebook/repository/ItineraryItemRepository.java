package com.ktb10.kgb.guidebook.repository;

import com.ktb10.kgb.guidebook.entity.ItineraryItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, Long> {

    List<ItineraryItem> findAllByItineraryDayIdInOrderByItineraryDayDayNumberAscSequenceAsc(
            Collection<Long> itineraryDayIds);
}
