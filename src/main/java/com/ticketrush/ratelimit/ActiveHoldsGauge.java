package com.ticketrush.ratelimit;

import com.ticketrush.hold.HoldRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ActiveHoldsGauge {
    private final AtomicLong value = new AtomicLong();
    private final HoldRepository holdRepository;

    public ActiveHoldsGauge(HoldRepository holdRepository, MeterRegistry registry) {
        this.holdRepository = holdRepository;
        Gauge.builder("ticketrush.booking.active_holds", value, AtomicLong::get)
                .description("System-wide count of currently ACTIVE holds")
                .register(registry);
    }

    @Scheduled(fixedDelay = 10_000)
    public void refresh() {
        value.set(holdRepository.countAllActive(OffsetDateTime.now()));
    }
}
