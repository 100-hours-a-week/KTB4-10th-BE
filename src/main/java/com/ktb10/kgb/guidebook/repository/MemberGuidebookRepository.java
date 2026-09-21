package com.ktb10.kgb.guidebook.repository;

import com.ktb10.kgb.guidebook.entity.MemberGuidebook;
import java.util.Optional;
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
              and memberGuidebook.guidebook.id = :guidebookId
              and memberGuidebook.deletedAt is null
            """)
    Optional<MemberGuidebook> findActiveWithGuidebook(
            @Param("memberId") Long memberId,
            @Param("guidebookId") Long guidebookId);
}
