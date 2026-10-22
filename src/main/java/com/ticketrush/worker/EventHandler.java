package com.ticketrush.worker;

import java.util.Set;

public interface EventHandler {
    String group();
    Set<String> eventTypes();
    void handle(EventMessage event) throws Exception;
}
