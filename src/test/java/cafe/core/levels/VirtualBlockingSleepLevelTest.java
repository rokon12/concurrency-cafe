package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualBlockingSleepLevelTest {

    private final VirtualBlockingSleepLevel level = new VirtualBlockingSleepLevel();

    @Test
    void starterStarvesPlatformPool() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed(),
            "starter funnels every blocking task through pool=2; collector never runs");
    }

    @Test
    void movingSleepersToVirtualPoolPasses() {
        Outcome outcome = level.run("""
            virtualPool.submit(() -> {
                Thread.sleep(250);
                receipts.put(1);
            });
            virtualPool.submit(() -> {
                Thread.sleep(250);
                receipts.put(2);
            });
            virtualPool.submit(() -> {
                Thread.sleep(250);
                receipts.put(3);
            });
            virtualPool.submit(() -> {
                Thread.sleep(250);
                receipts.put(4);
            });
            virtualPool.submit(() -> {
                Thread.sleep(250);
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
    void fullSourceContainsBothPoolsAndImports() {
        String full = level.fullSourceWith(level.starterCode());
        assertTrue(full.contains("import java.util.concurrent.ExecutorService"));
        assertTrue(full.contains("import java.util.concurrent.Executors"));
        assertTrue(full.contains("Executors.newFixedThreadPool(2)"));
        assertTrue(full.contains("Executors.newVirtualThreadPerTaskExecutor()"));
        assertTrue(full.contains("Thread.sleep(250)"));
    }
}
