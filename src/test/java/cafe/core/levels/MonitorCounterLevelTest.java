package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorCounterLevelTest {

    private final MonitorCounterLevel level = new MonitorCounterLevel();

    @Test
    void starterRacesAndLosesUpdates() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().contains("expected 6"));
    }

    @Test
    void synchronizingOnRegisterPasses() {
        Outcome outcome = level.run("""
            Thread chef1 = Thread.ofVirtual().start(() -> {
                synchronized (register) {
                    int x = servings;
                    servings = x + 1;
                    x = servings;
                    servings = x + 1;
                    x = servings;
                    servings = x + 1;
                }
            });

            Thread chef2 = Thread.ofVirtual().start(() -> {
                synchronized (register) {
                    int x = servings;
                    servings = x + 1;
                    x = servings;
                    servings = x + 1;
                    x = servings;
                    servings = x + 1;
                }
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }
}
