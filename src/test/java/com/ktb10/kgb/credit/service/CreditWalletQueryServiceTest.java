package com.ktb10.kgb.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditWalletQueryServiceTest {

    @Mock
    private CreditWalletRepository creditWalletRepository;

    @Mock
    private GenerationJobRepository generationJobRepository;

    private CreditWalletQueryService service;

    @BeforeEach
    void setUp() {
        service = new CreditWalletQueryService(
                creditWalletRepository,
                generationJobRepository);
    }

    @Test
    void returnsBalanceAndAllowsGenerationWithoutActiveJob() {
        CreditWallet wallet = mock(CreditWallet.class);
        when(wallet.getCreditBalance()).thenReturn(3);
        when(creditWalletRepository.findByMemberId(1L)).thenReturn(Optional.of(wallet));
        when(generationJobRepository.findFirstByMemberIdAndStatusInOrderByIdAsc(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.<Collection<GenerationStatus>>any()))
                .thenReturn(Optional.empty());

        var response = service.getWallet(1L);

        assertThat(response.creditBalance()).isEqualTo(3);
        assertThat(response.activeJobId()).isNull();
        assertThat(response.canGenerate()).isTrue();
    }

    @Test
    void blocksGenerationAndReturnsActiveJobId() {
        CreditWallet wallet = mock(CreditWallet.class);
        GenerationJob job = mock(GenerationJob.class);
        when(wallet.getCreditBalance()).thenReturn(3);
        when(job.getId()).thenReturn(99L);
        when(creditWalletRepository.findByMemberId(1L)).thenReturn(Optional.of(wallet));
        when(generationJobRepository.findFirstByMemberIdAndStatusInOrderByIdAsc(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.<Collection<GenerationStatus>>any()))
                .thenReturn(Optional.of(job));

        var response = service.getWallet(1L);

        assertThat(response.activeJobId()).isEqualTo(99L);
        assertThat(response.canGenerate()).isFalse();
    }
}
