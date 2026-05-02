package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LostUpdateLevelTest {

    private final LostUpdateLevel level = new LostUpdateLevel();

    @Test
    void starterCodeFailsBecauseOfLostUpdate() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().contains("lost"));
    }

    @Test
    void synchronizedFixPasses() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {
                synchronized (counterLock) {
                    int x = counter;
                    counter = x + 1;
                }
            });
            Thread.ofVirtual().start(() -> {
                synchronized (counterLock) {
                    int x = counter;
                    counter = x + 1;
                }
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void atomicIncrementOnIntCounterIsRejected() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {
                counter.incrementAndGet();
            });
            """);

        assertFalse(outcome.passed());
        assertTrue(outcome.hasErrors());
        assertTrue(outcome.errors().get(0).contains("int"),
            "expected error to mention type mismatch, got: " + outcome.errors().get(0));
    }

    @Test
    void parseErrorIsReportedAsError() {
        Outcome outcome = level.run("Thread.ofVirtual().start();");

        assertFalse(outcome.passed());
        assertTrue(outcome.hasErrors());
    }

    @Test
    void fullSourceContainsTypedDeclarations() {
        String full = level.fullSourceWith("Thread.ofVirtual().start(() -> { counter++; });");

        assertTrue(full.contains("public class LostUpdateLesson"));
        assertTrue(full.contains("static int counter = 41"));
        assertTrue(full.contains("static final Object counterLock = new Object()"));
    }
}
