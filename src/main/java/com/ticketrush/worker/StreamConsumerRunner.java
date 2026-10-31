package com.ticketrush.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@Profile("worker")
public class StreamConsumerRunner implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(StreamConsumerRunner.class);

    private final RedisConnectionFactory connectionFactory;
    private final List<StreamEventHandler> handlers;
    private final StreamMessageProcessor processor;
    @Value("${app.events.stream}") private String stream;
    @Value("${app.worker.autostart:true}") private boolean autostart;
    @Value("${app.worker.shutdown-drain-timeout-ms:35000}") private long drainTimeoutMs;

    private final String instanceId = System.getenv().getOrDefault("HOSTNAME", "local") + "-" + ProcessHandle.current().pid();
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private volatile boolean running;
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private volatile boolean acceptingNewWork = true;

    public StreamConsumerRunner(RedisConnectionFactory cf,
                                List<StreamEventHandler> handlers,
                                StreamMessageProcessor processor) {
        this.connectionFactory = cf;
        this.handlers = handlers;
        this.processor = processor;
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
        acceptingNewWork = true;
        container = StreamMessageListenerContainer.create(connectionFactory,
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .batchSize(10).pollTimeout(Duration.ofSeconds(2)).build());

        Map<String, List<StreamEventHandler>> handlersByGroup = handlers.stream()
                .collect(Collectors.groupingBy(StreamEventHandler::group));

        for (Map.Entry<String, List<StreamEventHandler>> entry : handlersByGroup.entrySet()) {
            String group = entry.getKey();
            List<StreamEventHandler> groupHandlers = entry.getValue();
            var request = StreamMessageListenerContainer.StreamReadRequest
                    .builder(StreamOffset.create(stream, ReadOffset.lastConsumed()))
                    .consumer(Consumer.from(group, consumerNameFor(group)))
                    .autoAcknowledge(false)
                    .cancelOnError(t -> false)
                    .errorHandler(t -> log.warn("stream read error group={}: {}", group, t.toString()))
                    .build();
            container.register(request, record -> {
                String eventType = record.getValue().get("eventType");
                StreamEventHandler target = groupHandlers.stream()
                        .filter(h -> h.eventTypes().contains(eventType))
                        .findFirst()
                        .orElse(groupHandlers.get(0));
                onMessage(target, record);
            });
        }
        container.start();
        running = true;
        log.info("Stream consumers started for groups: {}", handlersByGroup.keySet());
    }

    @Override
    public void stop(Runnable callback) {
        acceptingNewWork = false;                       // 1. stop accepting new messages
        new Thread(() -> {                                // 2. wait for in-flight jobs, bounded
            long deadline = System.currentTimeMillis() + drainTimeoutMs;
            while (inFlight.get() > 0 && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (inFlight.get() > 0) {
                log.warn("Shutdown deadline hit with {} job(s) still in-flight; left unacked, recoverable on next start", inFlight.get());
            }
            if (container != null) {
                container.stop();                       // 3. stop stream subscriptions
            }
            running = false;
            callback.run();                               // 4. tell Spring we're done
        }, "worker-shutdown-drain").start();
    }

    public int getInFlightCount() {
        return inFlight.get();
    }

    @Override
    public void stop() {
        CountDownLatch latch = new CountDownLatch(1);
        stop(latch::countDown);
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void onMessage(StreamEventHandler handler, MapRecord<String, String, String> record) {
        if (!acceptingNewWork) return;                 // SIGTERM already begun; sweeper picks this up after restart
        inFlight.incrementAndGet();
        try {
            processor.process(handler, record, false);
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private String consumerNameFor(String group) {
        String prefix = switch (group) {
            case "ticket-generation" -> "ticket-worker";
            case "email-notification" -> "email-worker";
            case "sales-aggregation" -> "sales-worker";
            default -> group;
        };
        return prefix + "-" + instanceId;
    }
}
