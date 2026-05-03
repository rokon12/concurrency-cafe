package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockOrderingLevelTest {

    private final LockOrderingLevel level = new LockOrderingLevel();

    @Test
    void starterDeadlocksFromInconsistentLockOrder() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().toLowerCase().contains("deadlock")
                || outcome.summary().toLowerCase().contains("halt"),
            "expected deadlock summary, got: " + outcome.summary());
    }

    @Test
    void consistentOrderKnifeFirstPasses() {
        Outcome outcome = level.run("""
            Thread chef1 = Thread.ofVirtual().start(() -> {
                knife.lock();
                pan.lock();
                meals++;
                pan.unlock();
                knife.unlock();
            });

            Thread chef2 = Thread.ofVirtual().start(() -> {
                knife.lock();
                pan.lock();
                meals++;
                pan.unlock();
                knife.unlock();
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void consistentOrderPanFirstAlsoPasses() {
        Outcome outcome = level.run("""
            Thread chef1 = Thread.ofVirtual().start(() -> {
                pan.lock();
                knife.lock();
                meals++;
                knife.unlock();
                pan.unlock();
            });

            Thread chef2 = Thread.ofVirtual().start(() -> {
                pan.lock();
                knife.lock();
                meals++;
                knife.unlock();
                pan.unlock();
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }
}
