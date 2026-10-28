package com.ktb10.kgb.guidebook.callback;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.guidebook.dto.response.GenerationStatusResponse;
import com.ktb10.kgb.guidebook.entity.GenerationStatus;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 생성 화면 전용 SSE입니다. 진행 캐시와 연결은 메모리에만 존재합니다. */
@Service
public class GenerationStreamService {

    private final Map<String, Connection> connections = new HashMap<>();
    private final Cache<Long, GenerationUpdate> latest = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(20)).build();
    private final int maxConnections;
    private final long timeoutMillis;

    public GenerationStreamService(
            @Value("${ai.generation.sse.max-connections:1000}") int maxConnections,
            @Value("${ai.generation.timeout:15m}") Duration timeout) {
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("생성 SSE 최대 연결 수는 양수여야 합니다.");
        }
        this.maxConnections = maxConnections;
        this.timeoutMillis = timeout.plusMinutes(1).toMillis();
    }

    public synchronized SseEmitter subscribe(
            Long memberId, String sessionHash, Long jobId,
            Supplier<GenerationStatusResponse> snapshot) {
        long memberCount = connections.values().stream()
                .filter(connection -> connection.memberId().equals(memberId)).count();
        if (connections.size() >= maxConnections || memberCount >= 5) {
            throw new BusinessException(CommonErrorCode.RATE_LIMIT_EXCEEDED);
        }
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        connections.put(id, new Connection(memberId, sessionHash, jobId, emitter));
        emitter.onCompletion(() -> remove(id));
        emitter.onTimeout(() -> remove(id));
        emitter.onError(ignored -> remove(id));
        try {
            // 등록과 최초 snapshot 전송을 로컬 publish와 직렬화해 순서 역전을 줄입니다.
            GenerationStatusResponse state = snapshot.get();
            GenerationUpdate cached = latest.getIfPresent(jobId);
            String requestId = jobId + "-" + state.attemptCount();
            boolean usable = cached != null && cached.requestId().equals(requestId)
                    && cached.status() == state.status();
            GenerationUpdate initial = usable ? cached : new GenerationUpdate(
                    memberId, jobId, requestId, 0, state.status(), null,
                    state.guidebookId(), state.error() == null ? null : state.error().code());
            send(emitter, initial);
            if (terminal(initial.status())) {
                connections.remove(id);
                emitter.complete();
            }
        } catch (IOException | RuntimeException exception) {
            connections.remove(id);
            emitter.completeWithError(exception);
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
        }
        return emitter;
    }

    public synchronized void publish(GenerationUpdate update) {
        GenerationUpdate previous = latest.getIfPresent(update.jobId());
        if (previous != null) {
            int oldAttempt = attempt(previous);
            int newAttempt = attempt(update);
            if (newAttempt < oldAttempt
                    || (newAttempt == oldAttempt && (terminal(previous.status())
                    || (!terminal(update.status()) && update.sequence() <= previous.sequence())))) {
                return;
            }
        }
        latest.put(update.jobId(), update);
        Iterator<Connection> iterator = connections.values().iterator();
        while (iterator.hasNext()) {
            Connection connection = iterator.next();
            if (!connection.jobId().equals(update.jobId())
                    || !connection.memberId().equals(update.memberId())) {
                continue;
            }
            try {
                send(connection.emitter(), update);
                if (terminal(update.status())) {
                    iterator.remove();
                    connection.emitter().complete();
                }
            } catch (IOException | RuntimeException exception) {
                iterator.remove();
                connection.emitter().completeWithError(exception);
            }
        }
    }

    public synchronized void disconnect(Long memberId, String sessionHash) {
        Iterator<Connection> iterator = connections.values().iterator();
        while (iterator.hasNext()) {
            Connection connection = iterator.next();
            if (connection.memberId().equals(memberId)
                    && (sessionHash == null || sessionHash.equals(connection.sessionHash()))) {
                iterator.remove();
                connection.emitter().complete();
            }
        }
    }

    @Scheduled(fixedDelay = 15000)
    public synchronized void heartbeat() {
        Iterator<Connection> iterator = connections.values().iterator();
        while (iterator.hasNext()) {
            SseEmitter emitter = iterator.next().emitter();
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | RuntimeException exception) {
                iterator.remove();
                emitter.completeWithError(exception);
            }
        }
    }

    private void send(SseEmitter emitter, GenerationUpdate update) throws IOException {
        emitter.send(SseEmitter.event().name("generation-status")
                .id(update.requestId() + ":" + update.sequence())
                .reconnectTime(3000).data(update));
    }

    private int attempt(GenerationUpdate update) {
        return Integer.parseInt(update.requestId().substring(update.requestId().lastIndexOf('-') + 1));
    }

    private boolean terminal(GenerationStatus status) {
        return status == GenerationStatus.COMPLETED || status == GenerationStatus.FAILED
                || status == GenerationStatus.CANCELED;
    }

    private synchronized void remove(String id) {
        connections.remove(id);
    }

    @PreDestroy
    public synchronized void close() {
        var closing = new ArrayList<>(connections.values());
        connections.clear();
        closing.forEach(connection -> connection.emitter().complete());
        latest.invalidateAll();
    }

    private record Connection(Long memberId, String sessionHash, Long jobId, SseEmitter emitter) {
    }
}
