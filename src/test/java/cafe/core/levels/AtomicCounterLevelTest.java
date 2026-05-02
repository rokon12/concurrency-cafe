package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicCounterLevelTest {

    private final AtomicCounterLevel level = new AtomicCounterLevel();

    @Test
    void emptyStarterFailsBecauseNeitherChefIncrements() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().contains("41") || outcome.summary().contains("expected"));
    }

    @Test
    void incrementAndGetInBothChefsPasses() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {
                counter.incrementAndGet();
            });
            Thread.ofVirtual().start(() -> {
                counter.incrementAndGet();
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void postIncrementOnAtomicIntegerIsRejectedWithHelpfulError() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {
                counter++;
            });
            """);

        assertFalse(outcome.passed());
        assertTrue(outcome.hasErrors());
        String err = outcome.errors().get(0);
        assertTrue(err.contains("AtomicInteger"), "expected error to mention AtomicInteger, got: " + err);
        assertTrue(err.contains("incrementAndGet"), "expected error to suggest incrementAndGet, got: " + err);
    }

    @Test
    void fullSourceContainsAtomicIntegerDeclaration() {
        String full = level.fullSourceWith("Thread.ofVirtual().start(() -> { counter.incrementAndGet(); });");

        assertTrue(full.contains("import java.util.concurrent.atomic.AtomicInteger"));
        assertTrue(full.contains("static final AtomicInteger counter = new AtomicInteger(41)"));
        assertTrue(full.contains("counter.incrementAndGet"));
    }
}
