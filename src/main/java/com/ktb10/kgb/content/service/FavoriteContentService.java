package com.ktb10.kgb.content.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.content.dto.FavoriteContentListResponse;
import com.ktb10.kgb.content.dto.FavoriteContentResponse;
import com.ktb10.kgb.content.repository.FavoriteContentQuery;
import com.ktb10.kgb.content.repository.FavoriteContentQuery.FavoriteContentRow;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관심 장소 등록·해제와 커서 목록 조회를 담당합니다. */
@Service
public class FavoriteContentService {

    private static final String CURSOR_SEPARATOR = "|";

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

    @Transactional(readOnly = true)
    public FavoriteContentListResponse getFavorites(
            Long memberId,
            String cursor,
            int size) {
        List<FavoriteContentRow> candidates;
        if (cursor == null) {
            candidates = favoriteContentQuery.findAll(memberId, size + 1);
        } else {
            FavoriteCursor decodedCursor = decodeCursor(cursor);
            candidates = favoriteContentQuery.findAllAfter(
                    memberId,
                    decodedCursor.createdAt(),
                    decodedCursor.favoriteId(),
                    size + 1);
        }

        boolean hasMore = candidates.size() > size;
        List<FavoriteContentRow> currentPage = candidates.stream()
                .limit(size)
                .toList();
        String nextCursor = hasMore
                ? encodeCursor(currentPage.get(currentPage.size() - 1))
                : null;
        return new FavoriteContentListResponse(
                currentPage.stream().map(FavoriteContentRow::item).toList(),
                nextCursor,
                hasMore);
    }

    private String encodeCursor(FavoriteContentRow row) {
        String value = row.favoritedAt()
                + CURSOR_SEPARATOR + row.favoriteId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private FavoriteCursor decodeCursor(String cursor) {
        try {
            String value = new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("커서 형식이 올바르지 않습니다.");
            }
            LocalDateTime createdAt = LocalDateTime.parse(parts[0]);
            long favoriteId = Long.parseLong(parts[1]);
            if (favoriteId <= 0) {
                throw new IllegalArgumentException("커서 ID는 양수여야 합니다.");
            }
            return new FavoriteCursor(createdAt, favoriteId);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new BusinessException(
                    CommonErrorCode.COMMON_VALIDATION_ERROR, exception);
        }
    }

    private record FavoriteCursor(LocalDateTime createdAt, Long favoriteId) {
    }
}
