package com.ktb10.kgb.content.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.content.dto.ContentDetailResponse;
import com.ktb10.kgb.content.dto.ContentListResponse;
import com.ktb10.kgb.content.dto.ContentListResponse.ContentListItemResponse;
import com.ktb10.kgb.content.model.ContentSearchCategory;
import com.ktb10.kgb.content.repository.ContentReadRepository;
import com.ktb10.kgb.content.repository.ContentReadRepository.ContentDetailProjection;
import com.ktb10.kgb.content.repository.ContentSearchQuery;
import com.ktb10.kgb.content.repository.FavoriteContentQuery;
import com.ktb10.kgb.content.repository.MapContentPersonalizationQuery;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 정보 탭의 행사 목록과 상세 정보를 제공합니다. */
@Service
public class ContentReadService {

    private final ContentReadRepository contentReadRepository;
    private final FavoriteContentQuery favoriteContentQuery;
    private final ContentSearchQuery contentSearchQuery;
    private final MapContentPersonalizationQuery personalizationQuery;

    public ContentReadService(
            ContentReadRepository contentReadRepository,
            FavoriteContentQuery favoriteContentQuery,
            ContentSearchQuery contentSearchQuery,
            MapContentPersonalizationQuery personalizationQuery) {
        this.contentReadRepository = contentReadRepository;
        this.favoriteContentQuery = favoriteContentQuery;
        this.contentSearchQuery = contentSearchQuery;
        this.personalizationQuery = personalizationQuery;
    }

    @Transactional(readOnly = true)
    public ContentListResponse getContents(
            Long memberId,
            ContentSearchCategory category,
            String rawQuery,
            String rawRegionCode,
            String rawMonth,
            int page,
            int size) {
        String query = normalizeQuery(category, rawQuery);
        String regionCode = normalizeRegionCode(rawRegionCode);
        YearMonth month = parseMonth(rawMonth);
        var result = contentSearchQuery.findContents(
                category,
                query,
                regionCode,
                month == null ? null : month.atDay(1),
                month == null ? null : month.atEndOfMonth(),
                page,
                size);
        var personalization = personalizationQuery.findByMemberAndContentIds(
                memberId,
                result.items().stream().map(item -> item.id()).toList());
        var items = result.items().stream()
                .map(item -> ContentListItemResponse.from(item, personalization))
                .toList();
        int totalPages = (int) Math.ceil((double) result.totalItems() / size);
        return new ContentListResponse(
                items, page, size, result.totalItems(), totalPages);
    }

    private String normalizeQuery(ContentSearchCategory category, String rawQuery) {
        String query = rawQuery == null ? null : rawQuery.trim();
        if (!StringUtils.hasText(query)) {
            if (category == ContentSearchCategory.EVENT) {
                return null;
            }
            throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_ERROR);
        }
        if (query.codePointCount(0, query.length()) > 10) {
            throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_ERROR);
        }
        return query;
    }

    private String normalizeRegionCode(String rawRegionCode) {
        if (rawRegionCode == null) {
            return null;
        }
        String regionCode = rawRegionCode.trim();
        if (!StringUtils.hasText(regionCode)
                || contentReadRepository.countProvinceByCode(regionCode) == 0) {
            throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_ERROR);
        }
        return regionCode;
    }

    private YearMonth parseMonth(String rawMonth) {
        if (rawMonth == null) {
            return null;
        }
        try {
            return YearMonth.parse(rawMonth);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(CommonErrorCode.COMMON_VALIDATION_ERROR);
        }
    }

    @Transactional(readOnly = true)
    public ContentDetailResponse getEventDetail(Long memberId, String contentId) {
        ContentDetailProjection detail = contentReadRepository.findEventDetail(contentId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        return ContentDetailResponse.from(
                detail,
                contentReadRepository.findImages(detail.getId()),
                favoriteContentQuery.exists(memberId, detail.getId()));
    }
}
