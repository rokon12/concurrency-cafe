package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualVsPlatformLevelTest {

    private final VirtualVsPlatformLevel level = new VirtualVsPlatformLevel();

    @Test
    void starterDeadlocksOnFixedPoolOfOne() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().toLowerCase().contains("deadlock")
                || outcome.summary().toLowerCase().contains("halt"),
            "expected deadlock when both submits go to fixedPool of size 1, got: "
                + outcome.summary());
    }

    @Test
    void switchingBothToVirtualPoolPasses() {
        Outcome outcome = level.run("""
            virtualPool.submit(() -> {
                queue.put(1);
                queue.put(2);
                queue.put(3);
                queue.put(4);
                queue.put(5);
            });

            virtualPool.submit(() -> {
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
    void mixedFixedAndVirtualPoolAlsoPasses() {
        // Producer holds the lone fixedPool slot and parks on full;
        // consumer is on virtualPool, unbounded, so it can drain freely.
        Outcome outcome = level.run("""
            fixedPool.submit(() -> {
                queue.put(1);
                queue.put(2);
                queue.put(3);
                queue.put(4);
                queue.put(5);
            });

            virtualPool.submit(() -> {
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
    void fullSourceContainsBothExecutorsAndImports() {
        String full = level.fullSourceWith(level.starterCode());
        assertTrue(full.contains("import java.util.concurrent.ExecutorService"));
        assertTrue(full.contains("import java.util.concurrent.Executors"));
        assertTrue(full.contains("Executors.newFixedThreadPool(1)"));
        assertTrue(full.contains("Executors.newVirtualThreadPerTaskExecutor()"));
    }
}
