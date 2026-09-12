package com.ticketrush.hold;

import com.ticketrush.hold.dto.HoldRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class NaiveHoldLoadTest {

    @Autowired
    private HoldService holdService;

    @Autowired
    private HoldRepository holdRepository;

    @Test
    public void executeLoadTest() throws InterruptedException {
        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();

        System.out.println("====== STARTING LOAD TEST ======");

        for (int i = 1; i <= threads; i++) {
            final long userId = (i % 200) + 1;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    holdService.createHold(1L, new HoldRequest(userId, List.of(42L)));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads at once
        endLatch.await(); // Wait for all threads to finish

        long holdCount = holdRepository.count();
        System.out.println("====== SUCCESSFUL HOLDS CREATED VIA API: " + successCount.get() + " ======");
        System.out.println("====== TOTAL HOLDS IN DATABASE: " + holdCount + " ======");
    }
}

