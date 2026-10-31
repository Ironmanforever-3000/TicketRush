package com.ticketrush.outbox;

import com.ticketrush.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
class OutboxPublisherIntegrationTest extends AbstractIntegrationTest {

    @SuppressWarnings("deprecation")
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }

    @Autowired
    OutboxBatchService batchService;

    @Autowired
    OutboxClaimService claimService;

    @Autowired
    StringRedisTemplate redis;

    @Value("${app.events.stream}")
    String stream;

    private StringRedisTemplate brokenRedisTemplate() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("127.0.0.1", 65432);
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    @Test
    void confirmedBookingEventuallyAppearsOnTheStream() throws Exception {
        Fixture f = bookedShow(2);
        batchService.publishBatch();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var records = redis.opsForStream().range(stream, Range.unbounded());
            assertThat(records).anySatisfy(r -> {
                assertThat(r.getValue().get("eventType")).isEqualTo("BOOKING_CONFIRMED");
                assertThat(r.getValue().get("payload")).asString().contains(f.bookingId().toString());
            });

            Boolean published = jdbc.queryForObject(
                    "SELECT published_at IS NOT NULL FROM outbox WHERE aggregate_id = ?", Boolean.class, f.bookingId());
            assertThat(published).isTrue();

            String claimedBy = jdbc.queryForObject(
                    "SELECT claimed_by FROM outbox WHERE aggregate_id = ?", String.class, f.bookingId());
            assertThat(claimedBy).isNotNull();
        });
    }

    @Test
    void redisFailureLeavesTheRowUnpublishedAndSafeToRetryLater() throws Exception {
        Fixture f = bookedShow(1);

        StringRedisTemplate broken = brokenRedisTemplate();
        OutboxBatchService failing = new OutboxBatchService(claimService, broken);
        ReflectionTestUtils.setField(failing, "stream", stream);
        ReflectionTestUtils.setField(failing, "batchSize", 100);
        ReflectionTestUtils.setField(failing, "leaseSeconds", 30);

        assertThat(failing.publishBatch()).isZero();
        Boolean stillUnpublished = jdbc.queryForObject(
                "SELECT published_at IS NULL FROM outbox WHERE aggregate_id = ?", Boolean.class, f.bookingId());
        assertThat(stillUnpublished).isTrue();

        // Simulate lease expiration so another publisher pass can re-claim it
        jdbc.update("UPDATE outbox SET claimed_at = now() - interval '35 seconds' WHERE aggregate_id = ?", f.bookingId());
        assertThat(batchService.publishBatch()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void republishingAfterResetKeepsTheSameEventId() throws Exception {
        Fixture f = bookedShow(1);
        batchService.publishBatch();
        Long outboxId = jdbc.queryForObject("SELECT id FROM outbox WHERE aggregate_id = ?", Long.class, f.bookingId());

        jdbc.update("UPDATE outbox SET published_at = NULL, claimed_at = NULL, claimed_by = NULL WHERE id = ?", outboxId);
        batchService.publishBatch();

        long matches = redis.opsForStream().range(stream, Range.unbounded()).stream()
                .filter(r -> String.valueOf(r.getValue().get("eventId")).equals(String.valueOf(outboxId)))
                .count();
        assertThat(matches).isEqualTo(2);
    }
}
