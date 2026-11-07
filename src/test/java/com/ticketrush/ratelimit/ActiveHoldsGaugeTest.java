package com.ticketrush.ratelimit;

import com.ticketrush.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveHoldsGaugeTest extends AbstractIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ActiveHoldsGauge activeHoldsGauge;

    @Test
    void activeHoldsGaugeRegistersAndRefreshes() {
        activeHoldsGauge.refresh();
        var gauge = meterRegistry.find("ticketrush.booking.active_holds").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isGreaterThanOrEqualTo(0.0);
    }
}
