package com.ticketrush.inbox;

import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class InboxService {
    public final AtomicLong skippedDuplicates = new AtomicLong();

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    public void runOnce(String group, String eventId, ThrowingRunnable work) throws Exception {
        // stub for TDD failure
    }
}
