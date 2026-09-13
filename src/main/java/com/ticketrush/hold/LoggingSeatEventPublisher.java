package com.ticketrush.hold;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoggingSeatEventPublisher implements SeatEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingSeatEventPublisher.class);

    @Override
    public void publishSeatsReleased(List<Long> seatIds) {
        log.info("SEAT_RELEASED seat_ids={}", seatIds);
    }
}
