package com.ktb10.kgb.credit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.repository.CreditTransactionRepository;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class MonthlyFreeCreditRefillMysqlIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MonthlyFreeCreditRefillService refillService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CreditWalletRepository creditWalletRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void concurrentReconciliationRefillsOnlyOnce() throws Exception {
        Long memberId = new TransactionTemplate(transactionManager).execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            Member member = memberRepository.save(Member.register(
                    OauthProvider.KAKAO,
                    UUID.randomUUID().toString(),
                    "여행자",
                    null,
                    null,
                    now));
            creditWalletRepository.save(CreditWallet.open(member, now));
            return member.getId();
        });

        CyclicBarrier startBarrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<MonthlyFreeCreditRefillResult> first = executor.submit(() -> {
                startBarrier.await();
                return refillService.refill(memberId, YearMonth.of(2026, 11));
            });
            Future<MonthlyFreeCreditRefillResult> second = executor.submit(() -> {
                startBarrier.await();
                return refillService.refill(memberId, YearMonth.of(2026, 11));
            });

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(
                            MonthlyFreeCreditRefillResult.REFILLED,
                            MonthlyFreeCreditRefillResult.ALREADY_PROCESSED);
        }

        assertThat(creditWalletRepository.findByMemberId(memberId).orElseThrow()
                .getCreditBalance()).isEqualTo(3);
        assertThat(creditTransactionRepository.findAll()).singleElement().satisfies(transaction -> {
            assertThat(transaction.getCreditDelta()).isEqualTo(3);
            assertThat(transaction.getIdempotencyKey())
                    .isEqualTo("monthly-free:" + memberId + ":202611");
        });
    }
}
