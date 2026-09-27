package com.ktb10.kgb.member.service;

import com.ktb10.kgb.member.dto.response.PreferenceOptionResponse;
import com.ktb10.kgb.member.entity.PreferenceCode;
import java.util.Arrays;
import org.springframework.stereotype.Service;

/** 코드로 확정된 V1 취향 선택지를 조회합니다. */
@Service
public class PreferenceOptionService {

    public PreferenceOptionResponse getOptions() {
        return new PreferenceOptionResponse(Arrays.stream(PreferenceCode.values())
                .map(PreferenceOptionResponse.Item::from)
                .toList());
    }
}
