package com.ticketrush.hold;

import com.ticketrush.seat.ReleasedSeatRow;
import com.ticketrush.seat.Seat;
import com.ticketrush.seat.SeatRepository;
import com.ticketrush.seat.SeatStateOutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final SeatRepository seatRepository;
    private final HoldRepository holdRepository;
    private final SeatStateOutboxWriter seatStateOutboxWriter;
    private final SeatEventPublisher seatEventPublisher;
    private final boolean isPostgres;

    public HoldExpiryJob(
            SeatRepository seatRepository,
            HoldRepository holdRepository,
            SeatStateOutboxWriter seatStateOutboxWriter,
            @Autowired(required = false) SeatEventPublisher seatEventPublisher,
            @Autowired(required = false) DataSource dataSource
    ) {
        this.seatRepository = seatRepository;
        this.holdRepository = holdRepository;
        this.seatStateOutboxWriter = seatStateOutboxWriter;
        this.seatEventPublisher = seatEventPublisher;

        boolean postgres = true;
        if (dataSource != null) {
            try (var conn = dataSource.getConnection()) {
                postgres = "PostgreSQL".equalsIgnoreCase(conn.getMetaData().getDatabaseProductName());
            } catch (Exception ignored) {
            }
        }
        this.isPostgres = postgres;
    }

    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void releaseExpiredHolds() {
        expireHolds();
    }

    @Transactional
    public void expireHolds() {
        OffsetDateTime now = OffsetDateTime.now();
        int expiredHoldsCount = holdRepository.expireOldHolds(now);
        if (expiredHoldsCount > 0) {
            log.info("Expired {} hold records", expiredHoldsCount);
        }

        List<ReleasedSeatRow> released;
        if (isPostgres) {
            released = seatRepository.releaseExpiredSeatsReturning();
        } else {
            // Fallback for non-PostgreSQL databases (e.g. H2 in-memory test dialect) that do not support RETURNING
            List<Long> expiredIds = seatRepository.findExpiredHeldSeatIds();
            if (expiredIds.isEmpty()) {
                return;
            }
            List<Seat> seats = seatRepository.findAllById(expiredIds);
            seatRepository.releaseHeldSeats(expiredIds);
            released = seats.stream().map(s -> (ReleasedSeatRow) new ReleasedSeatRow() {
                @Override public Long getId() { return s.getId(); }
                @Override public Long getShowId() { return s.getShow().getId(); }
            }).toList();
        }

        if (released == null || released.isEmpty()) {
            return;
        }

        log.info("Expired hold sweeper released {} seats across shows", released.size());

        released.stream()
                .collect(Collectors.groupingBy(ReleasedSeatRow::getShowId,
                        Collectors.mapping(ReleasedSeatRow::getId, Collectors.toList())))
                .forEach((showId, seatIds) -> seatStateOutboxWriter.write(showId, seatIds, "AVAILABLE"));

        if (seatEventPublisher != null) {
            List<Long> allSeatIds = released.stream().map(ReleasedSeatRow::getId).toList();
            seatEventPublisher.publishSeatsReleased(allSeatIds);
        }
    }
}
