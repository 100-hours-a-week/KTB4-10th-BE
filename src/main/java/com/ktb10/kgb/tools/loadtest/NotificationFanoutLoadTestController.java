package com.ktb10.kgb.tools.loadtest;

import com.ktb10.kgb.common.observability.NotificationCreationPhase;
import com.ktb10.kgb.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증된 fixture 세션으로만 호출하는 로컬 알림 fan-out 부하테스트 제어 API입니다. */
@Validated
@RestController
@Profile("local & loadtest")
@RequestMapping("/api/v1/load-tests/notification-fanout")
public class NotificationFanoutLoadTestController {

    private final NotificationFanoutLoadTestFixtureService fixtureService;
    private final NotificationFanoutLoadTestRunner runner;

    public NotificationFanoutLoadTestController(
            NotificationFanoutLoadTestFixtureService fixtureService,
            NotificationFanoutLoadTestRunner runner) {
        this.fixtureService = fixtureService;
        this.runner = runner;
    }

    @PostMapping("/fixtures")
    public ResponseEntity<ApiResponse<NotificationFanoutLoadTestFixtureService.PreparedFixture>>
            prepare(@Valid @RequestBody PrepareRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "notification_fanout_fixture_prepared",
                        fixtureService.prepare(
                                request.scenario(),
                                request.memberCount(),
                                request.pushSubscriptions())));
    }

    @PostMapping("/runs")
    public ResponseEntity<ApiResponse<NotificationFanoutLoadTestRunner.RunResult>> run(
            @Valid @RequestBody RunRequest request) {
        return ResponseEntity.accepted()
                .body(
                        ApiResponse.success(
                                "notification_fanout_run_accepted",
                                runner.run(request.scenario(), request.phase())));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<NotificationFanoutLoadTestFixtureService.FixtureStatus>>
            status() {
        return ResponseEntity.ok(
                ApiResponse.success("notification_fanout_status_success", fixtureService.status()));
    }

    @DeleteMapping("/fixtures")
    public ResponseEntity<Void> cleanup() {
        fixtureService.cleanup();
        return ResponseEntity.noContent().build();
    }

    public record PrepareRequest(
            @NotNull NotificationFanoutLoadTestScenario scenario,
            @Min(1) @Max(10_000) int memberCount,
            boolean pushSubscriptions) {}

    public record RunRequest(
            @NotNull NotificationFanoutLoadTestScenario scenario,
            @NotNull NotificationCreationPhase phase) {}
}
