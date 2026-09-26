package com.ktb10.kgb.guidebook.repository;

import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    Optional<GenerationJob> findByMemberIdAndIdempotencyKey(
            Long memberId, String idempotencyKey);

    boolean existsByMemberIdAndStatusIn(
            Long memberId, Collection<GenerationStatus> statuses);

    boolean existsByIdAndMemberId(Long id, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from GenerationJob job where job.id = :jobId")
    Optional<GenerationJob> findByIdForUpdate(@Param("jobId") Long jobId);
}
