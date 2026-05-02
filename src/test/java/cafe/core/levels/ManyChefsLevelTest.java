package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManyChefsLevelTest {

    private final ManyChefsLevel level = new ManyChefsLevel();

    @Test
    void starterFailsWithThreeLostUpdates() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().contains("lost"));
    }

    @Test
    void synchronizingAllFourChefsPasses() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {
                synchronized (counterLock) { int x = counter; counter = x + 1; }
            });
            Thread.ofVirtual().start(() -> {
                synchronized (counterLock) { int x = counter; counter = x + 1; }
            });
            Thread.ofVirtual().start(() -> {
                synchronized (counterLock) { int x = counter; counter = x + 1; }
            });
            Thread.ofVirtual().start(() -> {
                synchronized (counterLock) { int x = counter; counter = x + 1; }
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void synchronizingOnlyThreeChefsLeavesARace() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {
                synchronized (counterLock) { int x = counter; counter = x + 1; }
            });
            Thread.ofVirtual().start(() -> {
                synchronized (counterLock) { int x = counter; counter = x + 1; }
            });
            Thread.ofVirtual().start(() -> {
                synchronized (counterLock) { int x = counter; counter = x + 1; }
            });
            Thread.ofVirtual().start(() -> {
                int x = counter;
                counter = x + 1;
            });
            """);

        assertFalse(outcome.passed(), "expected a lost update when one chef skips the lock");
    }
}
