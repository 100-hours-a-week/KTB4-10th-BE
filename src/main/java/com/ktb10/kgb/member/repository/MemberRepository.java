package com.ktb10.kgb.member.repository;

import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 회원 조회와 회원 단위 쓰기 직렬화를 위한 Repository입니다. */
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByIdAndDeletedAtIsNull(Long id);

    Optional<Member> findByOauthProviderAndOauthSubjectAndDeletedAtIsNull(
            OauthProvider oauthProvider,
            String oauthSubject);

    @Query("select member.id from Member member "
            + "where member.deletedAt is null and member.id > :afterId "
            + "order by member.id")
    List<Long> findNonDeletedMemberIdsAfter(
            @Param("afterId") Long afterId,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id and m.deletedAt is null")
    Optional<Member> findActiveByIdForUpdate(@Param("id") Long id);
}
