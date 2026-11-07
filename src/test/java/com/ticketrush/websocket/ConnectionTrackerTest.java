package com.ticketrush.websocket;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionTrackerTest {

    @Test
    void tracksConnectAndDisconnect() {
        var tracker = new ConnectionTracker();
        tracker.connected("s1");
        tracker.connected("s2");
        assertThat(tracker.count()).isEqualTo(2);
        tracker.disconnected("s1");
        assertThat(tracker.count()).isEqualTo(1);
    }

    @Test
    void duplicateConnectDoesNotDoubleCount() {
        var tracker = new ConnectionTracker();
        tracker.connected("s1");
        tracker.connected("s1");
        assertThat(tracker.count()).isEqualTo(1);
    }
}
