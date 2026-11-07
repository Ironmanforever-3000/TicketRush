package com.ticketrush.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class QuotaMetrics {
    private final Counter quotaRejections;

    public QuotaMetrics(MeterRegistry registry) {
        this.quotaRejections = registry.counter("ticketrush.booking.quota.rejections");
    }

    public void recordRejection() {
        quotaRejections.increment();
    }
}
