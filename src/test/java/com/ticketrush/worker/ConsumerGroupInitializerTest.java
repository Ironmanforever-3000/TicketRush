package com.ticketrush.worker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatCode;

class ConsumerGroupInitializerTest extends AbstractWorkerIntegrationTest {
    @Autowired ConsumerGroupInitializer init;

    @Test
    void creatingTheSameGroupTwiceIsSafe() {
        init.ensureGroup("ticket-generation");
        assertThatCode(() -> init.ensureGroup("ticket-generation")).doesNotThrowAnyException();
    }

    @Test
    void concurrentStartupsDoNotFail() throws Exception {
        var pool = Executors.newFixedThreadPool(5);
        var futures = IntStream.range(0, 5).mapToObj(i -> pool.submit(() -> init.ensureGroup("sales-aggregation"))).toList();
        for (var f : futures) {
            f.get();
        }
        pool.shutdown();
    }
}
