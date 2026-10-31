package com.ticketrush.worker;

import java.util.Set;

public interface StreamEventHandler {
    String group();
    Set<String> eventTypes();
    void handle(StreamEvent event) throws Exception;
}
