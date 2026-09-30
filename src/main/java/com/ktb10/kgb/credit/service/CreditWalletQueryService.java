package com.ktb10.kgb.credit.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.credit.dto.response.CreditWalletResponse;
import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.error.CreditErrorCode;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원의 생성권 지갑과 진행 중인 생성 작업을 조회합니다. */
@Service
@RequiredArgsConstructor
public class CreditWalletQueryService {

    private static final Set<GenerationStatus> ACTIVE_STATUSES = EnumSet.of(
            GenerationStatus.PENDING,
            GenerationStatus.PROCESSING);

    private final CreditWalletRepository creditWalletRepository;
    private final GenerationJobRepository generationJobRepository;

    @Transactional(readOnly = true)
    public CreditWalletResponse getWallet(Long memberId) {
        CreditWallet wallet = creditWalletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.WALLET_NOT_FOUND));
        Long activeJobId = generationJobRepository
                .findFirstByMemberIdAndStatusInOrderByIdAsc(memberId, ACTIVE_STATUSES)
                .map(GenerationJob::getId)
                .orElse(null);
        return new CreditWalletResponse(
                wallet.getCreditBalance(),
                activeJobId,
                wallet.getCreditBalance() > 0 && activeJobId == null);
    }
}
