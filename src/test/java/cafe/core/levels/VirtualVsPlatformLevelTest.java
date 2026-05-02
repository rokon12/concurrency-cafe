package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualVsPlatformLevelTest {

    private final VirtualVsPlatformLevel level = new VirtualVsPlatformLevel();

    @Test
    void platformPoolSizeIsOne() {
        assertEquals(1, level.platformPoolSize());
    }

    @Test
    void starterDeadlocksUnderPlatformPoolOfOne() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().toLowerCase().contains("deadlock")
                || outcome.summary().toLowerCase().contains("halt"),
            "expected deadlock when both chefs are platform threads in pool=1, got: "
                + outcome.summary());
    }

    @Test
    void switchingBothToVirtualPasses() {
        Outcome outcome = level.run("""
            Thread producer = Thread.ofVirtual().start(() -> {
                queue.put(1);
                queue.put(2);
                queue.put(3);
                queue.put(4);
                queue.put(5);
            });

            Thread consumer = Thread.ofVirtual().start(() -> {
                int x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void mixedVirtualAndPlatformAlsoPasses() {
        // Producer stays platform (acquires the slot, parks on full); consumer is
        // virtual and not pool-bounded, so it can drain the queue freely.
        Outcome outcome = level.run("""
            Thread producer = Thread.ofPlatform().start(() -> {
                queue.put(1);
                queue.put(2);
                queue.put(3);
                queue.put(4);
                queue.put(5);
            });

            Thread consumer = Thread.ofVirtual().start(() -> {
                int x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }
}
