package com.ktb10.kgb.guidebook.repository;

import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.entity.JobType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {

    @Query(value = "SELECT * FROM generation_jobs "
            + "WHERE status = 'PUBLISH_PENDING' AND next_publish_at <= :now "
            + "AND (publish_claim_expires_at IS NULL OR publish_claim_expires_at <= :now) "
            + "ORDER BY next_publish_at, id LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<GenerationJob> findNextPublicationForUpdate(@Param("now") java.time.LocalDateTime now);

    Optional<GenerationJob> findByMemberIdAndIdempotencyKey(
            Long memberId, String idempotencyKey);

    Optional<GenerationJob> findFirstByGuidebookIdAndStatusOrderByCompletedAtDescIdDesc(
            Long guidebookId, GenerationStatus status);

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

}
