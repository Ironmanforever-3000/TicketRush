package com.ticketrush.hold;

import java.util.List;

public interface SeatEventPublisher {
    void publishSeatsReleased(List<Long> seatIds);
}
