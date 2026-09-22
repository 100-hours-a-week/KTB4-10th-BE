package com.ktb10.kgb.member.repository;

import com.ktb10.kgb.member.entity.MemberPreference;
import com.ktb10.kgb.member.entity.PreferenceCode;
import com.ktb10.kgb.member.entity.PreferenceType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 회원의 현재 취향 선택 집합을 조회하고 교체하기 위한 Repository입니다. */
public interface MemberPreferenceRepository extends JpaRepository<MemberPreference, Long> {

    List<MemberPreference> findAllByMemberIdOrderByPreferenceTypeAscPreferenceCodeAscIdAsc(
            Long memberId);

    boolean existsByMemberIdAndPreferenceTypeAndPreferenceCode(
            Long memberId,
            PreferenceType preferenceType,
            PreferenceCode preferenceCode);

    @Modifying(flushAutomatically = true)
    @Query("delete from MemberPreference p where p.member.id = :memberId")
    int deleteAllByMemberId(@Param("memberId") Long memberId);
}
