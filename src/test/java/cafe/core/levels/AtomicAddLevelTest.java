package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicAddLevelTest {

    private final AtomicAddLevel level = new AtomicAddLevel();

    @Test
    void emptyStarterFailsBecauseNoChefAdds() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed());
    }

    @Test
    void incrementAndGetIsNotEnoughForADeltaOfFive() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> { counter.incrementAndGet(); });
            Thread.ofVirtual().start(() -> { counter.incrementAndGet(); });
            """);

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().contains("expected 10"));
    }

    @Test
    void addAndGetFiveInBothChefsPasses() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> { counter.addAndGet(5); });
            Thread.ofVirtual().start(() -> { counter.addAndGet(5); });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void getAndAddIsAcceptedToo() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> { counter.getAndAdd(5); });
            Thread.ofVirtual().start(() -> { counter.getAndAdd(5); });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void postIncrementIsRejectedWithHelpfulError() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> { counter++; });
            """);

        assertFalse(outcome.passed());
        assertTrue(outcome.hasErrors());
        String err = outcome.errors().get(0);
        assertTrue(err.contains("AtomicInteger"));
    }
}
