package cafe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadlockKitchenLevelTest {

    private final DeadlockKitchenLevel level = new DeadlockKitchenLevel();

    @Test
    void starterCodeDeadlocks() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().toLowerCase().contains("deadlock"),
            "expected deadlock in summary, got: " + outcome.summary());
    }

    @Test
    void consistentLockOrderingPasses() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {
                synchronized (oven) {
                    synchronized (fryer) {
                        System.out.println("chef1 plates a dish");
                    }
                }
            });
            Thread.ofVirtual().start(() -> {
                synchronized (oven) {
                    synchronized (fryer) {
                        System.out.println("chef2 plates a dish");
                    }
                }
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void trivialEmptyChefsFailValidation() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {});
            Thread.ofVirtual().start(() -> {});
            """);

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().contains("oven") || outcome.summary().contains("fryer"));
    }

    @Test
    void onlyOneChefHoldingBothLocksStillFailsValidation() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {
                synchronized (oven) {
                    synchronized (fryer) {
                        System.out.println("chef1 plates a dish");
                    }
                }
            });
            Thread.ofVirtual().start(() -> {});
            """);

        assertFalse(outcome.passed(), "single-chef solution should fail: " + outcome.summary());
    }

    @Test
    void fullSourceContainsBothMonitors() {
        String full = level.fullSourceWith(level.starterCode());

        assertTrue(full.contains("static final Object oven = new Object()"));
        assertTrue(full.contains("static final Object fryer = new Object()"));
    }
}
