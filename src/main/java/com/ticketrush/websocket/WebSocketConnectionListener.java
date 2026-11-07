package com.ticketrush.websocket;

import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@Profile("!worker")
public class WebSocketConnectionListener {
    private final ConnectionTracker tracker;

    public WebSocketConnectionListener(ConnectionTracker tracker) {
        this.tracker = tracker;
    }

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        tracker.connected(accessor.getSessionId());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        tracker.disconnected(event.getSessionId());
    }
}
