package com.ticketrush.worker;

import java.time.Instant;

public record StreamEvent(String eventId, String eventType, String payload, Instant occurredAt) {}
