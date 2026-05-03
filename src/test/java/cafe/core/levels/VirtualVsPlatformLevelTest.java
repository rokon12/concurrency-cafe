package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualVsPlatformLevelTest {

    private final VirtualVsPlatformLevel level = new VirtualVsPlatformLevel();

    @Test
    void starterMissesTheDeadlineOnPlatformPool() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed(), "starter funnels every sleeper through pool=2");
        assertTrue(outcome.summary().toLowerCase().contains("ms")
                || outcome.summary().contains("expected"),
            "expected deadline-miss summary, got: " + outcome.summary());
    }

    @Test
    void movingSleepersToVirtualPoolPasses() {
        Outcome outcome = level.run("""
            virtualPool.submit(() -> {
                Thread.sleep(500);
                receipts.put(1);
            });
            virtualPool.submit(() -> {
                Thread.sleep(500);
                receipts.put(2);
            });
            virtualPool.submit(() -> {
                Thread.sleep(500);
                receipts.put(3);
            });
            virtualPool.submit(() -> {
                Thread.sleep(500);
                receipts.put(4);
            });
            virtualPool.submit(() -> {
                Thread.sleep(500);
                receipts.put(5);
            });

            platformPool.submit(() -> {
                int x = receipts.take();
                totalReceived = totalReceived + x;
                x = receipts.take();
                totalReceived = totalReceived + x;
                x = receipts.take();
                totalReceived = totalReceived + x;
                x = receipts.take();
                totalReceived = totalReceived + x;
                x = receipts.take();
                totalReceived = totalReceived + x;
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void allOnVirtualPoolAlsoPasses() {
        Outcome outcome = level.run("""
            virtualPool.submit(() -> { Thread.sleep(500); receipts.put(1); });
            virtualPool.submit(() -> { Thread.sleep(500); receipts.put(2); });
            virtualPool.submit(() -> { Thread.sleep(500); receipts.put(3); });
            virtualPool.submit(() -> { Thread.sleep(500); receipts.put(4); });
            virtualPool.submit(() -> { Thread.sleep(500); receipts.put(5); });

            virtualPool.submit(() -> {
                int x = receipts.take();
                totalReceived = totalReceived + x;
                x = receipts.take();
                totalReceived = totalReceived + x;
                x = receipts.take();
                totalReceived = totalReceived + x;
                x = receipts.take();
                totalReceived = totalReceived + x;
                x = receipts.take();
                totalReceived = totalReceived + x;
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void fullSourceContainsBothPoolsAndImports() {
        String full = level.fullSourceWith(level.starterCode());
        assertTrue(full.contains("import java.util.concurrent.ExecutorService"));
        assertTrue(full.contains("import java.util.concurrent.Executors"));
        assertTrue(full.contains("Executors.newFixedThreadPool(2)"));
        assertTrue(full.contains("Executors.newVirtualThreadPerTaskExecutor()"));
    }
}
