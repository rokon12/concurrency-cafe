package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitNotifyLevelTest {

    private final WaitNotifyLevel level = new WaitNotifyLevel();

    @Test
    void starterFailsBecauseConsumerRacesAhead() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().contains("expected 42"));
    }

    @Test
    void waitNotifyHandoffPasses() {
        Outcome outcome = level.run("""
            Thread consumer = Thread.ofVirtual().start(() -> {
                synchronized (lock) {
                    lock.wait();
                    result = data;
                }
            });

            Thread producer = Thread.ofVirtual().start(() -> {
                synchronized (lock) {
                    data = 42;
                    lock.notify();
                }
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void notifyAllAlsoWorks() {
        Outcome outcome = level.run("""
            Thread consumer = Thread.ofVirtual().start(() -> {
                synchronized (lock) {
                    lock.wait();
                    result = data;
                }
            });

            Thread producer = Thread.ofVirtual().start(() -> {
                synchronized (lock) {
                    data = 42;
                    lock.notifyAll();
                }
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void waitWithoutHoldingMonitorIsRuntimeError() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {
                lock.wait();
            });
            """);

        assertFalse(outcome.passed());
        assertTrue(outcome.hasErrors());
        assertTrue(outcome.errors().get(0).contains("IllegalMonitorStateException"));
    }

    @Test
    void notifyOnAtomicIntegerIsRejectedAtParseTime() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {
                data.notify();
            });
            """);

        assertFalse(outcome.passed());
        assertTrue(outcome.hasErrors());
        assertTrue(outcome.errors().get(0).contains("Object monitor"));
    }
}
