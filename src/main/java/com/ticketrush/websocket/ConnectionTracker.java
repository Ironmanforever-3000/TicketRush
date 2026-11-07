package com.ticketrush.websocket;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectionTracker {
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    public void connected(String sessionId) {
        if (sessionId != null) {
            sessions.add(sessionId);
        }
    }

    public void disconnected(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public int count() {
        return sessions.size();
    }
}
