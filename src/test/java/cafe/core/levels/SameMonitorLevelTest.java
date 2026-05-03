package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SameMonitorLevelTest {

    private final SameMonitorLevel level = new SameMonitorLevel();

    @Test
    void starterRacesBecauseMonitorsDiffer() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().contains("expected 4"));
    }

    @Test
    void usingPrepStationOnBothChefsPasses() {
        Outcome outcome = level.run("""
            Thread chef1 = Thread.ofVirtual().start(() -> {
                synchronized (prepStation) {
                    int x = plates;
                    plates = x + 1;
                    x = plates;
                    plates = x + 1;
                }
            });
            Thread chef2 = Thread.ofVirtual().start(() -> {
                synchronized (prepStation) {
                    int x = plates;
                    plates = x + 1;
                    x = plates;
                    plates = x + 1;
                }
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void usingPassWindowOnBothChefsPasses() {
        Outcome outcome = level.run("""
            Thread chef1 = Thread.ofVirtual().start(() -> {
                synchronized (passWindow) {
                    int x = plates;
                    plates = x + 1;
                    x = plates;
                    plates = x + 1;
                }
            });
            Thread chef2 = Thread.ofVirtual().start(() -> {
                synchronized (passWindow) {
                    int x = plates;
                    plates = x + 1;
                    x = plates;
                    plates = x + 1;
                }
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }
}
