package com.ktb10.kgb.member.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.common.security.oauth.KakaoUnlinkClient;
import com.ktb10.kgb.credit.entity.CreditTransaction;
import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 탈퇴에 필요한 회원·세션·생성 작업·생성권 정리를 조정합니다. */
@Service
public class MemberWithdrawalService {

    private static final EnumSet<GenerationStatus> ACTIVE_GENERATION_STATUSES = EnumSet.of(
            GenerationStatus.PENDING,
            GenerationStatus.PROCESSING);
    private static final String DEIDENTIFIED_SUBJECT_PREFIX = "withdrawn:";
    private static final String CREDIT_REVOKE_KEY_PREFIX = "withdrawal-revoke:";

    private final MemberRepository memberRepository;
    private final AuthSessionRepository authSessionRepository;
    private final CreditWalletRepository creditWalletRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final GenerationJobRepository generationJobRepository;
    private final KakaoUnlinkClient kakaoUnlinkClient;
    private final Clock clock;

    public MemberWithdrawalService(
            MemberRepository memberRepository,
            AuthSessionRepository authSessionRepository,
            CreditWalletRepository creditWalletRepository,
            CreditTransactionRepository creditTransactionRepository,
            GenerationJobRepository generationJobRepository,
            KakaoUnlinkClient kakaoUnlinkClient,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.authSessionRepository = authSessionRepository;
        this.creditWalletRepository = creditWalletRepository;
        this.creditTransactionRepository = creditTransactionRepository;
        this.generationJobRepository = generationJobRepository;
        this.kakaoUnlinkClient = kakaoUnlinkClient;
        this.clock = clock;
    }

    @Transactional
    public void withdraw(Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Member member = memberRepository.findActiveByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.AUTH_SESSION_REQUIRED));
        kakaoUnlinkClient.unlink(member.getOauthSubject());

        List<GenerationJob> activeJobs =
                generationJobRepository.findAllByMemberIdAndStatusInForUpdate(
                        memberId, ACTIVE_GENERATION_STATUSES);
        revokeCredits(memberId, now);
        activeJobs.forEach(job -> job.cancelForWithdrawal(now));
        member.withdraw(DEIDENTIFIED_SUBJECT_PREFIX + memberId, now);
        authSessionRepository.revokeAllActiveByMemberId(memberId, now);
    }

    private void revokeCredits(Long memberId, LocalDateTime now) {
        creditWalletRepository.findByMemberIdForUpdate(memberId).ifPresent(wallet -> {
            int revokedAmount = wallet.revokeAll(now);
            if (revokedAmount == 0) {
                return;
            }
            creditTransactionRepository.save(CreditTransaction.revoke(
                    wallet,
                    revokedAmount,
                    CREDIT_REVOKE_KEY_PREFIX + memberId,
                    now));
        });
    }
}
