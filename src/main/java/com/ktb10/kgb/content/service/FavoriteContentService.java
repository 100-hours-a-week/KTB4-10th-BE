package com.ktb10.kgb.content.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.content.dto.FavoriteContentResponse;
import com.ktb10.kgb.content.repository.FavoriteContentQuery;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지도 핀의 관심 장소 등록·해제를 담당합니다. */
@Service
public class FavoriteContentService {

    private final FavoriteContentQuery favoriteContentQuery;
    private final Clock clock;

    public FavoriteContentService(
            FavoriteContentQuery favoriteContentQuery,
            Clock clock) {
        this.favoriteContentQuery = favoriteContentQuery;
        this.clock = clock;
    }

    @Transactional
    public FavoriteContentResponse save(Long memberId, String contentId) {
        Long internalContentId = favoriteContentQuery.findActiveContentId(contentId)
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.RESOURCE_NOT_FOUND));
        favoriteContentQuery.save(
                memberId, internalContentId, LocalDateTime.now(clock));
        return new FavoriteContentResponse(contentId, true);
    }

    @Transactional
    public void delete(Long memberId, String contentId) {
        favoriteContentQuery.delete(memberId, contentId);
    }

}
