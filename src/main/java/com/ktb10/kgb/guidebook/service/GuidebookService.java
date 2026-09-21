package com.ktb10.kgb.guidebook.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.guidebook.dto.response.GuidebookDetailResponse;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.repository.MemberGuidebookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가이드북 조회, 일정, 삭제 등 일반적인 비즈니스 로직을 담당합니다. */
@Service
public class GuidebookService {

    private final MemberGuidebookRepository memberGuidebookRepository;

    public GuidebookService(MemberGuidebookRepository memberGuidebookRepository) {
        this.memberGuidebookRepository = memberGuidebookRepository;
    }

    @Transactional(readOnly = true)
    public GuidebookDetailResponse getGuidebookDetail(Long memberId, Long guidebookId) {
        Guidebook guidebook = memberGuidebookRepository
                .findActiveWithGuidebook(memberId, guidebookId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND))
                .getGuidebook();

        // TODO: 지역 도메인 조회 기능이 제공되면 행정 코드와 지역명을 상세 응답에 추가한다.
        return GuidebookDetailResponse.from(guidebook);
    }
}
