package com.ktb10.kgb.guidebook.repository;

import com.ktb10.kgb.guidebook.entity.AcquisitionType;
import com.ktb10.kgb.guidebook.entity.MemberGuidebook;
import com.ktb10.kgb.member.entity.MemberStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberGuidebookRepository extends JpaRepository<MemberGuidebook, Long> {

    @Query(
            """
            select memberGuidebook
            from MemberGuidebook memberGuidebook
            join fetch memberGuidebook.guidebook
            where memberGuidebook.member.id = :memberId
              and memberGuidebook.deletedAt is null
            order by memberGuidebook.guidebook.startDate asc,
                     memberGuidebook.guidebook.createdAt desc,
                     memberGuidebook.id desc
            """)
    List<MemberGuidebook> findActiveGuidebooks(
            @Param("memberId") Long memberId,
            Pageable pageable);

    @Query(
            """
            select memberGuidebook
            from MemberGuidebook memberGuidebook
            join fetch memberGuidebook.guidebook
            where memberGuidebook.member.id = :memberId
              and memberGuidebook.deletedAt is null
              and (memberGuidebook.guidebook.startDate > :startDate
                or (memberGuidebook.guidebook.startDate = :startDate
                  and memberGuidebook.guidebook.createdAt < :createdAt)
                or (memberGuidebook.guidebook.startDate = :startDate
                  and memberGuidebook.guidebook.createdAt = :createdAt
                  and memberGuidebook.id < :id))
            order by memberGuidebook.guidebook.startDate asc,
                     memberGuidebook.guidebook.createdAt desc,
                     memberGuidebook.id desc
            """)
    List<MemberGuidebook> findActiveGuidebooksAfter(
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("id") Long id,
            Pageable pageable);

    @Query(
            """
            select memberGuidebook
            from MemberGuidebook memberGuidebook
            join fetch memberGuidebook.guidebook
            where memberGuidebook.member.id = :memberId
              and memberGuidebook.guidebook.id = :guidebookId
              and memberGuidebook.deletedAt is null
            """)
    Optional<MemberGuidebook> findActiveWithGuidebook(
            @Param("memberId") Long memberId,
            @Param("guidebookId") Long guidebookId);

    @Query(
            """
            select memberGuidebook
            from MemberGuidebook memberGuidebook
            join fetch memberGuidebook.member member
            join fetch memberGuidebook.guidebook guidebook
            where memberGuidebook.id > :afterId
              and memberGuidebook.acquisitionType = :acquisitionType
              and memberGuidebook.deletedAt is null
              and guidebook.startDate = :startDate
              and guidebook.contentHtml is not null
              and member.status = :memberStatus
              and member.deletedAt is null
            order by memberGuidebook.id asc
            """)
    List<MemberGuidebook> findTripStartingCandidatesAfter(
            @Param("startDate") LocalDate startDate,
            @Param("acquisitionType") AcquisitionType acquisitionType,
            @Param("memberStatus") MemberStatus memberStatus,
            @Param("afterId") Long afterId,
            Pageable pageable);
}
