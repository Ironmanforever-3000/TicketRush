package com.ticketrush.concurrency;

import com.ticketrush.auth.RefreshTokenGenerator;
import com.ticketrush.auth.RefreshTokenService;
import com.ticketrush.user.User;
import com.ticketrush.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class RefreshTokenConcurrencyTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenGenerator tokenGenerator;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testConcurrentTokenRotation() throws InterruptedException {
        // Setup user
        User user = new User();
        user.setName("Concurrency Test");
        user.setEmail("concur" + System.currentTimeMillis() + "@test.local");
        user.setPasswordHash("hash");
        user.setRole(com.ticketrush.auth.Role.CUSTOMER);
        user.setCreatedAt(java.time.OffsetDateTime.now());
        user.setUpdatedAt(java.time.OffsetDateTime.now());
        user.setActive(true);
        user = userRepository.save(user);

        // Generate initial token
        String initialPlainToken = refreshTokenService.createToken(user);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await(); // wait until all threads are ready
                    
                    // Attempt to validate/rotate
                    var result = refreshTokenService.validateToken(initialPlainToken);
                    if (result.isPresent()) {
                        refreshTokenService.rotateToken(initialPlainToken);
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown(); // start all threads
        doneLatch.await(); // wait for all to finish
        executor.shutdown();

        // With pessimistic locking, only 1 thread should succeed. 
        // The others will either block and see usedAt != null, or throw exception.
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);
    }
}
