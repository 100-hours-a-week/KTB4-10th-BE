package com.ktb10.kgb.guidebook.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.common.response.ApiResponse;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.response.GuidebookGenerationResponse;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.entity.JobType;
import com.ktb10.kgb.guidebook.service.GuidebookGenerationService;
import com.ktb10.kgb.member.entity.MemberStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class GuidebookGenerationControllerTest {

    @Mock
    private GuidebookGenerationService guidebookGenerationService;

    @InjectMocks
    private GuidebookGenerationController controller;

    @Test
    void acceptsInitialGenerationRequest() {
        AuthenticatedMember member = new AuthenticatedMember(
                1L,
                MemberStatus.ACTIVE,
                10L);
        GuidebookGenerationRequest request = new GuidebookGenerationRequest(
                "경상북도",
                "경주시",
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 14),
                Companion.FRIEND,
                2);
        GuidebookGenerationResponse serviceResponse = new GuidebookGenerationResponse(
                301L,
                JobType.INITIAL,
                GenerationStatus.PENDING,
                null);
        given(guidebookGenerationService.createInitial(1L, "request-key", request))
                .willReturn(serviceResponse);

        var result = controller.createInitial(member, "request-key", request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(result.getBody())
                .isEqualTo(ApiResponse.success(
                        "guidebook_generation_accepted",
                        serviceResponse));
        verify(guidebookGenerationService).createInitial(1L, "request-key", request);
    }
}
