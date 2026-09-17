package com.ktb10.kgb.member.repository;

import com.ktb10.kgb.member.entity.AuthSession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 세션 해시 조회와 회원별 세션 폐기를 위한 Repository입니다. */
public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    Optional<AuthSession> findBySessionIdHash(byte[] sessionIdHash);

    List<AuthSession> findAllByMemberIdAndRevokedAtIsNullOrderByCreatedAtAsc(Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthSession session set session.revokedAt = :revokedAt "
            + "where session.member.id = :memberId and session.revokedAt is null")
    int revokeAllActiveByMemberId(
            @Param("memberId") Long memberId,
            @Param("revokedAt") LocalDateTime revokedAt);
}
