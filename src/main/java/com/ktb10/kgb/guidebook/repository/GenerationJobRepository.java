package com.ktb10.kgb.guidebook.repository;

import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.entity.JobType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    Optional<GenerationJob> findByMemberIdAndIdempotencyKey(
            Long memberId, String idempotencyKey);

    Optional<GenerationJob> findFirstByGuidebookIdAndStatusAndAiJobIdIsNotNullOrderByCompletedAtDescIdDesc(
            Long guidebookId, GenerationStatus status);

    Optional<GenerationJob> findFirstByGuidebookIdAndJobTypeAndStatusOrderByCompletedAtAscIdAsc(
            Long guidebookId, JobType jobType, GenerationStatus status);

    boolean existsByMemberIdAndStatusIn(
            Long memberId, Collection<GenerationStatus> statuses);

    Optional<GenerationJob> findFirstByMemberIdAndStatusInOrderByIdAsc(
            Long memberId, Collection<GenerationStatus> statuses);

    boolean existsByIdAndMemberId(Long id, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from GenerationJob job "
            + "where job.member.id = :memberId and job.status in :statuses order by job.id")
    List<GenerationJob> findAllByMemberIdAndStatusInForUpdate(
            @Param("memberId") Long memberId,
            @Param("statuses") Collection<GenerationStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from GenerationJob job where job.id = :id")
    Optional<GenerationJob> findByIdForUpdate(@Param("id") Long id);

    @Query("select job.id from GenerationJob job "
            + "where job.status in :statuses and job.aiJobId is not null "
            + "and job.id > :afterId order by job.id")
    List<Long> findSynchronizableIdsAfter(
            @Param("statuses") Collection<GenerationStatus> statuses,
            @Param("afterId") Long afterId,
            Pageable pageable);
}
