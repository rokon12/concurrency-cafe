package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReentrantLockLevelTest {

    private final ReentrantLockLevel level = new ReentrantLockLevel();

    @Test
    void starterCodeFailsBecauseOfLostUpdate() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().contains("lost"));
    }

    @Test
    void manualLockUnlockPasses() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {
                counterLock.lock();
                int x = counter;
                counter = x + 1;
                counterLock.unlock();
            });
            Thread.ofVirtual().start(() -> {
                counterLock.lock();
                int x = counter;
                counter = x + 1;
                counterLock.unlock();
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void synchronizedOnReentrantLockIsRejected() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {
                synchronized (counterLock) {
                    int x = counter;
                    counter = x + 1;
                }
            });
            """);

        assertFalse(outcome.passed());
        assertTrue(outcome.hasErrors());
        String err = outcome.errors().get(0);
        assertTrue(err.contains("ReentrantLock"),
            "expected error to mention ReentrantLock, got: " + err);
    }

    @Test
    void fullSourceImportsReentrantLock() {
        String full = level.fullSourceWith(level.starterCode());

        assertTrue(full.contains("import java.util.concurrent.locks.ReentrantLock"));
        assertTrue(full.contains("static final ReentrantLock counterLock = new ReentrantLock()"));
    }
}
