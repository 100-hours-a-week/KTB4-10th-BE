package com.ktb10.kgb.content.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.content.dto.ContentDetailResponse;
import com.ktb10.kgb.content.dto.ContentListItemResponse;
import com.ktb10.kgb.content.dto.ContentListResponse;
import com.ktb10.kgb.content.repository.ContentDetailData;
import com.ktb10.kgb.content.repository.ContentReadQuery;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 정보 탭의 행사 목록과 상세 정보를 제공합니다. */
@Service
public class ContentReadService {

    private final ContentReadQuery contentReadQuery;

    public ContentReadService(ContentReadQuery contentReadQuery) {
        this.contentReadQuery = contentReadQuery;
    }

    @Transactional(readOnly = true)
    public ContentListResponse getEvents(String rawQuery, int page, int size) {
        String query = StringUtils.hasText(rawQuery) ? rawQuery.trim() : null;
        long totalItems = contentReadQuery.countEvents(query);
        List<ContentListItemResponse> items = contentReadQuery.findEvents(
                        query,
                        size,
                        (long) (page - 1) * size).stream()
                .map(ContentListItemResponse::from)
                .toList();
        int totalPages = totalItems == 0
                ? 0
                : Math.toIntExact((totalItems + size - 1) / size);
        return new ContentListResponse(items, page, size, totalItems, totalPages);
    }

    @Transactional(readOnly = true)
    public ContentDetailResponse getEventDetail(String contentId) {
        ContentDetailData detail = contentReadQuery.findEventDetail(contentId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        return ContentDetailResponse.from(detail, contentReadQuery.findImages(detail.id()));
    }
}
