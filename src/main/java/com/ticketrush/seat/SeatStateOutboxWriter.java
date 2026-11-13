package com.ticketrush.seat;

import com.ticketrush.cache.SeatMapCache;
import com.ticketrush.outbox.OutboxEvent;
import com.ticketrush.outbox.OutboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class SeatStateOutboxWriter {
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final SeatMapCache seatMapCache;

    public SeatStateOutboxWriter(OutboxRepository outbox, ObjectMapper mapper, SeatMapCache seatMapCache) {
        this.outbox = outbox;
        this.mapper = mapper;
        this.seatMapCache = seatMapCache;
    }

    /** Must run inside the caller's own transaction — the seat update and this outbox row
     *  commit together, or not at all. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(Long showId, List<Long> seatIds, String status) {
        OutboxEvent e = new OutboxEvent();
        e.setAggregateType("SHOW");
        e.setAggregateId(showId);
        e.setEventType("SEAT_STATE_CHANGED");
        e.setCreatedAt(OffsetDateTime.now());
        e.setPayload("{}");                 // placeholder; real payload needs the id this flush generates
        outbox.saveAndFlush(e);             // forces the INSERT now so e.getId() is populated

        try {
            e.setPayload(mapper.writeValueAsString(new SeatStateChangedEvent(
                    e.getId(), showId, seatIds, status, e.getId(), e.getCreatedAt().toInstant())));
            // e is still managed in this persistence context — JPA's dirty checking issues the
            // UPDATE at commit automatically; no second save() call needed.
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize seat state event", ex);
        }

        // Bump the seatmap cache version so stale reads expire naturally
        seatMapCache.bumpVersion(showId, e.getId());
    }
}
