package com.ktb10.kgb.guidebook.repository;

import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    Optional<GenerationJob> findByMemberIdAndIdempotencyKey(
            Long memberId, String idempotencyKey);

    boolean existsByMemberIdAndStatusIn(
            Long memberId, Collection<GenerationStatus> statuses);
}
