package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicReadModifyWriteLevelTest {

    private final AtomicReadModifyWriteLevel level = new AtomicReadModifyWriteLevel();

    @Test
    void starterStillRacesEvenThoughOrdersIsAtomic() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().contains("expected 6"));
    }

    @Test
    void incrementAndGetPasses() {
        Outcome outcome = level.run("""
            Thread chef1 = Thread.ofVirtual().start(() -> {
                orders.incrementAndGet();
                orders.incrementAndGet();
                orders.incrementAndGet();
            });

            Thread chef2 = Thread.ofVirtual().start(() -> {
                orders.incrementAndGet();
                orders.incrementAndGet();
                orders.incrementAndGet();
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }
}
