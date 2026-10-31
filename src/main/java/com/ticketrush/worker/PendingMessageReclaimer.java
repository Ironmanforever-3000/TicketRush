package com.ticketrush.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Profile("worker")
public class PendingMessageReclaimer {
    private static final Logger log = LoggerFactory.getLogger(PendingMessageReclaimer.class);

    private final StringRedisTemplate redis;
    private final List<StreamEventHandler> handlers;
    private final DeadLetterPublisher deadLetter;
    private final StreamMessageProcessor processor;

    @Value("${app.events.stream:ticketrush:events}")
    private String stream;

    @Value("${app.worker.pending-idle-seconds:30}")
    private long pendingIdleSeconds;

    @Value("${app.worker.max-deliveries:5}")
    private int maxDeliveries;

    private final String instanceId = System.getenv().getOrDefault("HOSTNAME", "local") + "-" + ProcessHandle.current().pid() + "-sweeper";

    public PendingMessageReclaimer(StringRedisTemplate redis,
                                   List<StreamEventHandler> handlers,
                                   DeadLetterPublisher deadLetter,
                                   StreamMessageProcessor processor) {
        this.redis = redis;
        this.handlers = handlers;
        this.deadLetter = deadLetter;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${app.worker.sweep-delay-ms:5000}")
    public void reclaimAbandoned() {
        Duration idle = Duration.ofSeconds(pendingIdleSeconds);
        Map<String, List<StreamEventHandler>> handlersByGroup = handlers.stream()
                .collect(Collectors.groupingBy(StreamEventHandler::group));

        for (Map.Entry<String, List<StreamEventHandler>> entry : handlersByGroup.entrySet()) {
            String group = entry.getKey();
            List<StreamEventHandler> groupHandlers = entry.getValue();

            PendingMessages pending = redis.<String, String>opsForStream().pending(stream, group, Range.unbounded(), 100);
            if (pending == null) continue;

            for (PendingMessage pm : pending) {
                if (pm.getElapsedTimeSinceLastDelivery().compareTo(idle) < 0) {
                    continue;   // still being worked; leave it
                }

                // getTotalDeliveryCount() is maintained by Redis per (stream, group, record) —
                // it survives a worker restart because it lives in Redis, not in our process.
                var claimed = redis.<String, String>opsForStream().claim(stream, group, instanceId,
                        XClaimOptions.minIdle(idle).ids(pm.getId()));            // minIdle: only one sweeper wins the claim
                if (claimed == null) continue;

                for (MapRecord<String, String, String> rec : claimed) {
                    boolean giveUp = pm.getTotalDeliveryCount() >= maxDeliveries;
                    String eventType = rec.getValue().get("eventType");
                    StreamEventHandler targetHandler = groupHandlers.stream()
                            .filter(h -> h.eventTypes().contains(eventType))
                            .findFirst()
                            .orElse(groupHandlers.get(0));

                    processor.process(targetHandler, rec, giveUp);

                    if (giveUp) {
                        log.error("DEAD-LETTER group={} id={} deliveries={}", group, rec.getId(), pm.getTotalDeliveryCount());
                    }
                }
            }
        }
    }
}
