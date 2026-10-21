package com.ticketrush.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    @Query(value = "SELECT * FROM outbox WHERE published_at IS NULL ORDER BY id ASC LIMIT 100 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByIdAsc();
}
