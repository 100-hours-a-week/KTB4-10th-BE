package com.ktb10.kgb.content.service;

import com.ktb10.kgb.content.repository.NewEventQuery;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.service.NotificationService;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** 기준일 신규 행사를 한 건으로 묶어 ACTIVE 회원에게 알립니다. */
@Service
public class NewEventDigestNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            NewEventDigestNotificationService.class);
    private static final int MEMBER_BATCH_SIZE = 100;
    private static final String BODY = "정보 탭에서 새로 등록된 행사를 확인해 보세요.";

    private final NewEventQuery newEventQuery;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    public NewEventDigestNotificationService(
            NewEventQuery newEventQuery,
            MemberRepository memberRepository,
            NotificationService notificationService) {
        this.newEventQuery = newEventQuery;
        this.memberRepository = memberRepository;
        this.notificationService = notificationService;
    }

    public NewEventDigestNotificationSummary createForReferenceDate(LocalDate referenceDate) {
        long newEventCount = newEventQuery.countBySourceCreatedAt(
                referenceDate.atStartOfDay(),
                referenceDate.plusDays(1).atStartOfDay());
        if (newEventCount == 0L) {
            return new NewEventDigestNotificationSummary(0L, 0, 0, 0, 0);
        }

        int targetMembers = 0;
        int created = 0;
        int duplicated = 0;
        int failed = 0;
        long lastMemberId = 0L;
        String title = title(newEventCount);
        String referenceId = referenceDate.toString();

        while (true) {
            List<Long> memberIds = memberRepository.findMemberIdsByStatusAfter(
                    MemberStatus.ACTIVE,
                    lastMemberId,
                    PageRequest.of(0, MEMBER_BATCH_SIZE));
            if (memberIds.isEmpty()) {
                break;
            }
            for (Long memberId : memberIds) {
                targetMembers++;
                try {
                    boolean inserted = notificationService.createOnce(
                            memberId,
                            NotificationType.NEW_EVENTS_ADDED,
                            title,
                            BODY,
                            NotificationReferenceType.EVENT_LIST,
                            referenceId,
                            referenceId);
                    if (inserted) {
                        created++;
                    } else {
                        duplicated++;
                    }
                } catch (RuntimeException exception) {
                    failed++;
                    LOGGER.error(
                            "신규 행사 묶음 알림 생성에 실패했습니다. "
                                    + "referenceDate={}, memberId={}",
                            referenceDate,
                            memberId,
                            exception);
                }
            }
            lastMemberId = memberIds.getLast();
        }

        return new NewEventDigestNotificationSummary(
                newEventCount,
                targetMembers,
                created,
                duplicated,
                failed);
    }

    private String title(long newEventCount) {
        if (newEventCount == 1L) {
            return "새로운 행사가 추가되었어요!";
        }
        return "새로운 행사가 %d개 추가되었어요!".formatted(newEventCount);
    }
}
