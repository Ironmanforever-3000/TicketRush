package com.ticketrush.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    // Claims up to batchSize unpublished rows for this one call, using a fresh random token
    // per call. FOR UPDATE SKIP LOCKED means two publishers never claim the same row, and the
    // transaction is short — it commits before any Redis I/O happens.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE outbox
           SET claimed_at = now(), claimed_by = :batchToken
         WHERE id IN (
             SELECT id FROM outbox
              WHERE published_at IS NULL
                AND (claimed_at IS NULL OR claimed_at < now() - CAST(:leaseSeconds AS INT) * INTERVAL '1' SECOND)
              ORDER BY id
              LIMIT :batchSize
              FOR UPDATE SKIP LOCKED
         )
        """, nativeQuery = true)
    int claimBatch(@Param("batchToken") String batchToken,
                   @Param("leaseSeconds") int leaseSeconds,
                   @Param("batchSize") int batchSize);

    List<OutboxEvent> findByClaimedByAndPublishedAtIsNull(String batchToken);

    @Modifying(clearAutomatically = true)
    @Query("update OutboxEvent e set e.publishedAt = :now where e.id = :id")
    int markPublished(@Param("id") Long id, @Param("now") OffsetDateTime now);

    @Query(value = "SELECT * FROM outbox WHERE published_at IS NULL ORDER BY id ASC LIMIT 100", nativeQuery = true)
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByIdAsc();
}
