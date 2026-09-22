package com.ktb10.kgb.member.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.member.dto.request.PreferenceSelectionRequest;
import com.ktb10.kgb.member.dto.request.PreferenceUpdateRequest;
import com.ktb10.kgb.member.dto.response.MemberPreferenceResponse;
import com.ktb10.kgb.member.dto.response.PreferenceSelectionResponse;
import com.ktb10.kgb.member.dto.response.PreferenceUpdateResponse;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.MemberPreference;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.member.entity.PreferenceCode;
import com.ktb10.kgb.member.entity.PreferenceType;
import com.ktb10.kgb.member.error.MemberErrorCode;
import com.ktb10.kgb.member.repository.MemberPreferenceRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원의 현재 취향을 조회하고 유효한 전체 선택 집합으로 교체합니다. */
@Service
public class MemberPreferenceService {

    private static final int MIN_THEME_COUNT = 1;
    private static final int MAX_THEME_COUNT = 3;
    private static final int MIN_DETAIL_COUNT_PER_THEME = 1;
    private static final int MAX_DETAIL_COUNT_PER_THEME = 3;
    private static final int MAX_TRAVEL_STYLE_COUNT = 4;

    private static final Comparator<MemberPreference> PREFERENCE_ORDER =
            Comparator.comparingInt((MemberPreference preference) ->
                            preference.getPreferenceType().ordinal())
                    .thenComparingInt(preference -> preference.getPreferenceCode().ordinal());

    private final MemberRepository memberRepository;
    private final MemberPreferenceRepository memberPreferenceRepository;
    private final Clock clock;

    public MemberPreferenceService(
            MemberRepository memberRepository,
            MemberPreferenceRepository memberPreferenceRepository,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.memberPreferenceRepository = memberPreferenceRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MemberPreferenceResponse getPreferences(Long memberId) {
        requireMember(memberId);
        return new MemberPreferenceResponse(toResponses(
                memberPreferenceRepository
                        .findAllByMemberIdOrderByPreferenceTypeAscPreferenceCodeAscIdAsc(
                                memberId)));
    }

    @Transactional
    public PreferenceUpdateResponse replacePreferences(
            Long memberId,
            PreferenceUpdateRequest request) {
        List<PreferenceCode> selections = validate(request.selections());
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.AUTH_SESSION_REQUIRED));

        memberPreferenceRepository.deleteAllByMemberId(memberId);
        List<MemberPreference> saved = memberPreferenceRepository.saveAll(
                selections.stream()
                        .map(code -> MemberPreference.select(member, code))
                        .toList());

        if (member.getStatus() == MemberStatus.ONBOARDING) {
            member.activate(LocalDateTime.now(clock));
        }

        return new PreferenceUpdateResponse(toResponses(saved), member.getStatus());
    }

    private Member requireMember(Long memberId) {
        return memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.AUTH_SESSION_REQUIRED));
    }

    private List<PreferenceCode> validate(List<PreferenceSelectionRequest> requestedSelections) {
        Set<PreferenceCode> uniqueCodes = EnumSet.noneOf(PreferenceCode.class);
        for (PreferenceSelectionRequest selection : requestedSelections) {
            PreferenceType type = parseType(selection.preferenceType());
            PreferenceCode code = parseCode(selection.preferenceCode());
            if (code.type() != type || !uniqueCodes.add(code)) {
                throw invalidPreference();
            }
        }

        Set<PreferenceCode> themes = codesOfType(uniqueCodes, PreferenceType.THEME);
        if (themes.size() < MIN_THEME_COUNT || themes.size() > MAX_THEME_COUNT) {
            throw invalidPreference();
        }

        Set<PreferenceCode> styles = codesOfType(uniqueCodes, PreferenceType.TRAVEL_STYLE);
        if (styles.size() > MAX_TRAVEL_STYLE_COUNT) {
            throw invalidPreference();
        }

        Map<PreferenceCode, Integer> detailCounts = new EnumMap<>(PreferenceCode.class);
        for (PreferenceCode code : codesOfType(uniqueCodes, PreferenceType.DETAIL)) {
            PreferenceCode parent = code.parent();
            if (parent == null || !themes.contains(parent)) {
                throw invalidPreference();
            }
            detailCounts.merge(parent, 1, Integer::sum);
        }

        for (PreferenceCode theme : themes) {
            int count = detailCounts.getOrDefault(theme, 0);
            if (count < MIN_DETAIL_COUNT_PER_THEME || count > MAX_DETAIL_COUNT_PER_THEME) {
                throw invalidPreference();
            }
        }

        return uniqueCodes.stream()
                .sorted(Comparator.comparingInt((PreferenceCode code) -> code.type().ordinal())
                        .thenComparingInt(PreferenceCode::ordinal))
                .toList();
    }

    private PreferenceType parseType(String value) {
        try {
            return PreferenceType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalidPreference(exception);
        }
    }

    private PreferenceCode parseCode(String value) {
        try {
            return PreferenceCode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalidPreference(exception);
        }
    }

    private Set<PreferenceCode> codesOfType(
            Set<PreferenceCode> codes,
            PreferenceType type) {
        Set<PreferenceCode> result = EnumSet.noneOf(PreferenceCode.class);
        codes.stream()
                .filter(code -> code.type() == type)
                .forEach(result::add);
        return result;
    }

    private List<PreferenceSelectionResponse> toResponses(List<MemberPreference> preferences) {
        List<MemberPreference> sorted = new ArrayList<>(preferences);
        sorted.sort(PREFERENCE_ORDER);
        return sorted.stream()
                .map(PreferenceSelectionResponse::from)
                .toList();
    }

    private BusinessException invalidPreference() {
        return new BusinessException(MemberErrorCode.PREFERENCE_INVALID);
    }

    private BusinessException invalidPreference(Throwable cause) {
        return new BusinessException(MemberErrorCode.PREFERENCE_INVALID, cause);
    }
}
