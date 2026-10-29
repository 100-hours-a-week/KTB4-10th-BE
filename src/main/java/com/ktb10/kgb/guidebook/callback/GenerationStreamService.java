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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 생성 화면 전용 SSE입니다. 진행 캐시와 연결은 메모리에만 존재합니다. */
@Service
public class GenerationStreamService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationStreamService.class);

    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    // 연결 수 검사와 등록/제거만 보호한다. DB 조회와 전송은 이 잠금 밖에서 한다.
    private final Object registryLock = new Object();
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

    public SseEmitter subscribe(
            Long memberId, String sessionHash, Long jobId,
            Supplier<GenerationStatusResponse> snapshot) {
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        Connection connection = new Connection(memberId, sessionHash, jobId, emitter);
        emitter.onCompletion(() -> remove(id));
        emitter.onTimeout(() -> remove(id));
        emitter.onError(ignored -> remove(id));
        synchronized (registryLock) {
            long memberCount = connections.values().stream()
                    .filter(value -> value.memberId.equals(memberId)).count();
            if (connections.size() >= maxConnections || memberCount >= 5) {
                throw new BusinessException(CommonErrorCode.RATE_LIMIT_EXCEEDED);
            }
            connections.put(id, connection);
        }
        try {
            // 조회 중 수신한 이벤트는 보관하고 초기 상태보다 최신인 값을 먼저 전송한다.
            GenerationStatusResponse state = snapshot.get();
            String requestId = jobId + "-" + state.attemptCount();
            GenerationUpdate initial = new GenerationUpdate(
                    memberId, jobId, requestId, 0, state.status(), null,
                    state.guidebookId(), state.error() == null ? null : state.error().code());
            synchronized (connection) {
                if (connections.get(id) == connection) {
                    connection.newest = newer(connection.newest, newer(latest.getIfPresent(jobId), initial));
                    connection.initialized = true;
                    sendUpdate(id, connection, connection.newest);
                }
            }
        } catch (RuntimeException exception) {
            remove(id);
            completeQuietly(emitter);
            throw exception;
        }
        return emitter;
    }

    public void publish(GenerationUpdate update) {
        GenerationUpdate accepted = latest.asMap().compute(
                update.jobId(), (jobId, previous) -> newer(previous, update));
        if (accepted != update) {
            return;
        }
        connections.forEach((id, connection) -> {
            if (!connection.jobId.equals(update.jobId()) || !connection.memberId.equals(update.memberId())) {
                return;
            }
            synchronized (connection) {
                if (connections.get(id) != connection || newer(connection.newest, update) == connection.newest) {
                    return;
                }
                connection.newest = update;
                if (connection.initialized) {
                    sendUpdate(id, connection, update);
                }
            }
        });
    }

    public void disconnect(Long memberId, String sessionHash) {
        connections.forEach((id, connection) -> {
            if (connection.memberId.equals(memberId)
                    && (sessionHash == null || sessionHash.equals(connection.sessionHash))) {
                remove(id);
                completeQuietly(connection.emitter);
            }
        });
    }

    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        connections.forEach((id, connection) -> {
            synchronized (connection) {
                if (!connection.initialized || connections.get(id) != connection) {
                    return;
                }
                try {
                    connection.emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | RuntimeException exception) {
                    remove(id);
                }
            }
        });
    }

    private void sendUpdate(String id, Connection connection, GenerationUpdate update) {
        try {
            send(connection.emitter, update);
        } catch (IOException | RuntimeException exception) {
            // send 실패 후 종료 처리는 컨테이너에 맡기고 다른 연결 처리를 계속한다.
            remove(id);
            return;
        }
        if (terminal(update.status())) {
            remove(id);
            completeQuietly(connection.emitter);
        }
    }

    private GenerationUpdate newer(GenerationUpdate previous, GenerationUpdate update) {
        if (update == null) {
            return previous;
        }
        if (previous == null) {
            return update;
        }
        if (attempt(update) != attempt(previous)) {
            return attempt(update) > attempt(previous) ? update : previous;
        }
        if (terminal(previous.status())
                || (previous.status() == GenerationStatus.PROCESSING && update.status() == GenerationStatus.PENDING)) {
            return previous;
        }
        // DB snapshot(sequence=0)은 캐시의 pending보다 앞선 상태일 수 있다.
        if (previous.status() == GenerationStatus.PENDING && update.status() == GenerationStatus.PROCESSING
                && update.sequence() == 0) {
            return update;
        }
        return terminal(update.status()) || update.sequence() > previous.sequence() ? update : previous;
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

    private void remove(String id) {
        synchronized (registryLock) {
            connections.remove(id);
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException exception) {
            LOGGER.debug("생성 SSE 연결 종료 처리에 실패했습니다.", exception);
        }
    }

    @PreDestroy
    public void close() {
        ArrayList<Connection> closing;
        synchronized (registryLock) {
            closing = new ArrayList<>(connections.values());
            connections.clear();
        }
        closing.forEach(connection -> completeQuietly(connection.emitter));
        latest.invalidateAll();
    }

    private static final class Connection {
        private final Long memberId;
        private final String sessionHash;
        private final Long jobId;
        private final SseEmitter emitter;
        private boolean initialized;
        private GenerationUpdate newest;

        private Connection(Long memberId, String sessionHash, Long jobId, SseEmitter emitter) {
            this.memberId = memberId;
            this.sessionHash = sessionHash;
            this.jobId = jobId;
            this.emitter = emitter;
        }
    }
}
