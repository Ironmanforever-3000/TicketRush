package com.ticketrush.hold;

import com.ticketrush.seat.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final SeatRepository seatRepository;
    private final SeatEventPublisher seatEventPublisher;

    public HoldExpiryJob(SeatRepository seatRepository, SeatEventPublisher seatEventPublisher) {
        this.seatRepository = seatRepository;
        this.seatEventPublisher = seatEventPublisher;
    }

    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void expireHolds() {
        List<Long> expiredSeatIds = seatRepository.findExpiredHeldSeatIds();

        if (expiredSeatIds.isEmpty()) {
            return;
        }

        int updatedRows = seatRepository.releaseHeldSeats(expiredSeatIds);

        if (updatedRows > 0) {
            log.info("Expired hold sweeper released {} seats: {}", updatedRows, expiredSeatIds);
            seatEventPublisher.publishSeatsReleased(expiredSeatIds);
        }
    }
}
