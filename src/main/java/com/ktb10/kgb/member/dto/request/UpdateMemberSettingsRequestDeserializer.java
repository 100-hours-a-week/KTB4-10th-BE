package com.ktb10.kgb.member.dto.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.util.Set;

/** 설정 변경 요청에서 V1이 지원하는 Boolean 필드만 허용합니다. */
public class UpdateMemberSettingsRequestDeserializer
        extends JsonDeserializer<UpdateMemberSettingsRequest> {

    private static final String PUSH_ENABLED = "push_enabled";
    private static final Set<String> ALLOWED_FIELDS = Set.of(PUSH_ENABLED);

    @Override
    public UpdateMemberSettingsRequest deserialize(
            JsonParser parser,
            DeserializationContext context) throws IOException {
        JsonNode root = parser.getCodec().readTree(parser);
        if (!root.isObject()) {
            throw MismatchedInputException.from(
                    parser,
                    UpdateMemberSettingsRequest.class,
                    "회원 설정 요청은 JSON 객체여야 합니다.");
        }

        boolean hasUnknownField = root.propertyStream()
                .anyMatch(property -> !ALLOWED_FIELDS.contains(property.getKey()));
        if (hasUnknownField) {
            throw MismatchedInputException.from(
                    parser,
                    UpdateMemberSettingsRequest.class,
                    "V1에서 지원하지 않는 회원 설정 필드입니다.");
        }

        JsonNode pushEnabled = root.get(PUSH_ENABLED);
        if (pushEnabled == null || pushEnabled.isNull()) {
            return new UpdateMemberSettingsRequest(null);
        }
        if (!pushEnabled.isBoolean()) {
            throw MismatchedInputException.from(
                    parser,
                    UpdateMemberSettingsRequest.class,
                    "push_enabled는 Boolean이어야 합니다.");
        }
        return new UpdateMemberSettingsRequest(pushEnabled.booleanValue());
    }
}
