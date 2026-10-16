package com.ktb10.kgb.content.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.content.dto.ContentDetailResponse;
import com.ktb10.kgb.content.dto.ContentListResponse;
import com.ktb10.kgb.content.dto.ContentListResponse.ContentListItemResponse;
import com.ktb10.kgb.content.repository.ContentReadRepository;
import com.ktb10.kgb.content.repository.ContentReadRepository.ContentDetailProjection;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 정보 탭의 행사 목록과 상세 정보를 제공합니다. */
@Service
public class ContentReadService {

    private final ContentReadRepository contentReadRepository;

    public ContentReadService(ContentReadRepository contentReadRepository) {
        this.contentReadRepository = contentReadRepository;
    }

    @Transactional(readOnly = true)
    public ContentListResponse getEvents(String rawQuery, int page, int size) {
        String query = StringUtils.hasText(rawQuery) ? rawQuery.trim() : null;
        var result = contentReadRepository.findEvents(query, PageRequest.of(page - 1, size));
        var items = result.getContent().stream()
                .map(ContentListItemResponse::from)
                .toList();
        return new ContentListResponse(
                items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ContentDetailResponse getEventDetail(String contentId) {
        ContentDetailProjection detail = contentReadRepository.findEventDetail(contentId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        return ContentDetailResponse.from(detail, contentReadRepository.findImages(detail.getId()));
    }
}
