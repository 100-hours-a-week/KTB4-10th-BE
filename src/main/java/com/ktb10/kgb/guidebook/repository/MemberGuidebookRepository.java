package com.ktb10.kgb.guidebook.repository;

import com.ktb10.kgb.guidebook.entity.MemberGuidebook;
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
}
