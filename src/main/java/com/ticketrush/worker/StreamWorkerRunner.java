package com.ticketrush.worker;

import com.ticketrush.inbox.InboxService;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true")
public class StreamWorkerRunner implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(StreamWorkerRunner.class);

    private final StringRedisTemplate redis;
    private final RedisConnectionFactory connectionFactory;
    private final List<EventHandler> handlers;
    private final InboxService inbox;
    private final ObjectMapper mapper;

    @Value("${app.events.stream}")
    private String stream;

    @Value("${app.events.dead-letter-stream}")
    private String deadLetterStream;

    @Value("${app.worker.max-attempts}")
    private int maxAttempts;

    @Value("${app.worker.max-deliveries:3}")
    private int maxDeliveries;

    @Value("${app.worker.concurrency:2}")
    private int concurrency;

    @Value("${app.worker.base-delay-ms}")
    private long baseDelayMs;

    @Value("${app.worker.max-delay-ms}")
    private long maxDelayMs;

    @Value("${app.worker.pending-idle-seconds}")
    private long pendingIdleSeconds;

    @Value("${app.worker.autostart:true}")
    private boolean autostart;

    private final String consumerBase = System.getenv().getOrDefault("HOSTNAME", "local") + "-" + ProcessHandle.current().pid();

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private volatile boolean running;

    public StreamWorkerRunner(StringRedisTemplate redis,
                              RedisConnectionFactory connectionFactory,
                              List<EventHandler> handlers,
                              InboxService inbox,
                              ObjectMapper mapper) {
        this.redis = redis;
        this.connectionFactory = connectionFactory;
        this.handlers = handlers != null ? handlers : List.of();
        this.inbox = inbox;
        this.mapper = mapper;
    }

    public void ensureGroups() {
        for (EventHandler h : handlers) {
            try {
                redis.execute((RedisCallback<String>) c -> c.streamCommands().xGroupCreate(
                        stream.getBytes(StandardCharsets.UTF_8), h.group(), ReadOffset.from("0"), true));
            } catch (RuntimeException e) {
                if (!String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage()).contains("BUSYGROUP")) {
                    throw e;
                }
            }
        }
    }

    @Override
    public boolean isAutoStartup() {
        return autostart;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public synchronized void start() {
        if (running) return;
        ensureGroups();
        container = StreamMessageListenerContainer.create(connectionFactory,
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .batchSize(10).pollTimeout(Duration.ofSeconds(2)).build());
        for (EventHandler h : handlers) {
            for (int i = 1; i <= concurrency; i++) {
                var request = StreamMessageListenerContainer.StreamReadRequest
                        .builder(StreamOffset.create(stream, ReadOffset.lastConsumed()))
                        .consumer(Consumer.from(h.group(), consumerBase + "-" + i))
                        .autoAcknowledge(false)
                        .cancelOnError(t -> false)
                        .errorHandler(t -> log.warn("stream read error: {}", t.toString()))
                        .build();
                container.register(request, rec -> process(h, rec));
            }
        }
        container.start();
        running = true;
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (container != null) {
            container.stop();
        }
    }

    void process(EventHandler h, MapRecord<String, String, String> rec) {
        Map<String, String> f = rec.getValue();
        if (!h.eventTypes().contains(f.get("event_type"))) {
            ack(h.group(), rec);
            return;
        }
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                EventMessage msg = parse(f);
                inbox.runOnce(h.group(), msg.eventId(), () -> h.handle(msg));
                ack(h.group(), rec);
                return;
            } catch (PermanentFailureException e) {
                last = e;
                break;
            } catch (Exception e) {
                last = e;
                log.warn("handler {} failed (attempt {}/{}): {}", h.group(), attempt, maxAttempts, e.toString());
                if (attempt < maxAttempts && !pause(backoffMs(attempt))) return;
            }
        }
        deadLetter(h.group(), rec, last);
        ack(h.group(), rec);
    }

    @Scheduled(fixedDelayString = "${app.worker.sweep-delay-ms:5000}")
    public void recoverPending() {
        if (!running) return;
        var ops = redis.<String, String>opsForStream();
        Duration idle = Duration.ofSeconds(pendingIdleSeconds);
        for (EventHandler h : handlers) {
            PendingMessages pending = ops.pending(stream, h.group(), Range.unbounded(), 100);
            if (pending == null) continue;
            for (PendingMessage pm : pending) {
                if (pm.getElapsedTimeSinceLastDelivery().compareTo(idle) < 0) continue;
                var claimed = ops.claim(stream, h.group(), consumerBase + "-sweeper",
                        XClaimOptions.minIdle(idle).ids(pm.getId()));
                if (claimed == null) continue;
                for (var rec : claimed) {
                    if (pm.getTotalDeliveryCount() >= maxDeliveries) {
                        deadLetter(h.group(), rec, new IllegalStateException("max deliveries exceeded"));
                        ack(h.group(), rec);
                    } else {
                        process(h, rec);
                    }
                }
            }
        }
    }

    private EventMessage parse(Map<String, String> f) {
        try {
            if (f.get("event_id") == null || f.get("event_type") == null) {
                throw new IllegalArgumentException("missing fields");
            }
            return new EventMessage(f.get("event_id"), f.get("event_type"), mapper.readTree(f.get("payload")));
        } catch (Exception e) {
            throw new PermanentFailureException("malformed event " + f.get("event_id"), e);
        }
    }

    private void ack(String group, MapRecord<String, String, String> rec) {
        redis.opsForStream().acknowledge(stream, group, rec.getId());
    }

    private void deadLetter(String group, MapRecord<String, String, String> rec, Exception cause) {
        var f = new LinkedHashMap<>(rec.getValue());
        f.put("group", group);
        f.put("original_id", rec.getId().getValue());
        f.put("error", String.valueOf(cause));
        f.put("failed_at", Instant.now().toString());
        redis.opsForStream().add(StreamRecords.newRecord().in(deadLetterStream).ofMap(f));
        log.error("DEAD-LETTER group={} id={} cause={}", group, rec.getId(), String.valueOf(cause));
    }

    private long backoffMs(int attempt) {
        long cap = Math.min(maxDelayMs, baseDelayMs * (1L << Math.min(attempt - 1, 20)));
        return ThreadLocalRandom.current().nextLong(cap / 2, cap + 1);
    }

    private boolean pause(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
