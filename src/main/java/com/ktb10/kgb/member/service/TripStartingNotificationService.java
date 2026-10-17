package com.ktb10.kgb.member.service;

import com.ktb10.kgb.guidebook.entity.AcquisitionType;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.MemberGuidebook;
import com.ktb10.kgb.guidebook.repository.MemberGuidebookRepository;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** 여행 시작 전날의 생성자에게 알림을 생성합니다. */
@Service
public class TripStartingNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            TripStartingNotificationService.class);
    private static final int BATCH_SIZE = 100;
    private static final String TITLE = "여행 일정이 곧 시작돼요";
    private static final String BODY_FORMAT = "'%s' 여행이 내일 시작됩니다.";

    private final MemberGuidebookRepository memberGuidebookRepository;
    private final NotificationService notificationService;

    public TripStartingNotificationService(
            MemberGuidebookRepository memberGuidebookRepository,
            NotificationService notificationService) {
        this.memberGuidebookRepository = memberGuidebookRepository;
        this.notificationService = notificationService;
    }

    public TripStartingNotificationSummary createForStartDate(LocalDate startDate) {
        int created = 0;
        int duplicated = 0;
        int failed = 0;
        long lastMemberGuidebookId = 0L;

        while (true) {
            List<MemberGuidebook> candidates =
                    memberGuidebookRepository.findTripStartingCandidatesAfter(
                            startDate,
                            AcquisitionType.CREATED,
                            MemberStatus.ACTIVE,
                            lastMemberGuidebookId,
                            PageRequest.of(0, BATCH_SIZE));
            if (candidates.isEmpty()) {
                break;
            }
            for (MemberGuidebook candidate : candidates) {
                try {
                    if (createNotification(candidate, startDate)) {
                        created++;
                    } else {
                        duplicated++;
                    }
                } catch (RuntimeException exception) {
                    failed++;
                    LOGGER.error(
                            "여행 일정 임박 알림 생성에 실패했습니다. "
                                    + "memberGuidebookId={}, memberId={}, guidebookId={}",
                            candidate.getId(),
                            candidate.getMember().getId(),
                            candidate.getGuidebook().getId(),
                            exception);
                }
            }
            lastMemberGuidebookId = candidates.getLast().getId();
        }

        return new TripStartingNotificationSummary(created, duplicated, failed);
    }

    private boolean createNotification(
            MemberGuidebook memberGuidebook,
            LocalDate startDate) {
        Guidebook guidebook = memberGuidebook.getGuidebook();
        return notificationService.createOnce(
                memberGuidebook.getMember().getId(),
                NotificationType.TRIP_STARTING,
                TITLE,
                BODY_FORMAT.formatted(guidebook.getTitle()),
                NotificationReferenceType.GUIDEBOOK,
                guidebook.getId().toString(),
                startDate.toString());
    }
}
