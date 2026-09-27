package com.ktb10.kgb.guidebook.client;

import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.member.entity.PreferenceCode;
import com.ktb10.kgb.member.entity.PreferenceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 저장된 최초 생성 입력을 AI 서버의 생성 요청 계약으로 변환합니다. */
@Component
public class AiGuidebookRequestMapper {

    private final AiGuidebookContentQuery contentQuery;

    public AiGuidebookRequestMapper(AiGuidebookContentQuery contentQuery) {
        this.contentQuery = contentQuery;
    }

    public AiGuidebookRequest map(InitialGenerationRequestPayload payload) {
        GuidebookGenerationRequest request = payload.request();
        List<String> largeCategory = new ArrayList<>();
        Map<String, List<String>> midCategory = new LinkedHashMap<>();
        List<String> travelStyle = new ArrayList<>();

        for (InitialGenerationRequestPayload.PreferenceSnapshot snapshot
                : payload.preferences()) {
            PreferenceType type = PreferenceType.valueOf(snapshot.preferenceType());
            PreferenceCode code = PreferenceCode.valueOf(snapshot.preferenceCode());
            validatePreferenceType(type, code);

            switch (type) {
                case THEME -> largeCategory.add(code.label());
                case DETAIL -> midCategory
                        .computeIfAbsent(code.parent().label(), ignored -> new ArrayList<>())
                        .add(code.label());
                case TRAVEL_STYLE -> travelStyle.add(code.label());
                default -> throw new IllegalStateException("지원하지 않는 취향 타입입니다: " + type);
            }
        }

        return new AiGuidebookRequest(
                new AiGuidebookRequest.Region(request.province(), request.city()),
                request.startDate(),
                request.endDate(),
                request.companion().name().toLowerCase(Locale.ROOT),
                request.peopleCount(),
                new AiGuidebookRequest.Preferences(
                        largeCategory,
                        midCategory,
                        travelStyle),
                contentQuery.findAll(request.province(), request.city()));
    }

    private void validatePreferenceType(PreferenceType snapshotType, PreferenceCode code) {
        if (code.type() != snapshotType) {
            throw new IllegalArgumentException(
                    "취향 타입과 코드가 일치하지 않습니다: " + code.name());
        }
    }
}
