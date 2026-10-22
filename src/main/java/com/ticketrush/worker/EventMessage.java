package com.ticketrush.worker;

import tools.jackson.databind.JsonNode;

public record EventMessage(String eventId, String eventType, JsonNode payload) {}
