package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProducerConsumerLevelTest {

    private final ProducerConsumerLevel level = new ProducerConsumerLevel();

    @Test
    void starterDeadlocksOnTheMissingFifthPut() {
        Outcome outcome = level.run(level.starterCode());

        assertFalse(outcome.passed(), "starter ships with a bug; should not pass");
        assertTrue(outcome.summary().toLowerCase().contains("deadlock")
                || outcome.summary().toLowerCase().contains("halt"),
            "expected deadlock when consumer waits on missing item, got: " + outcome.summary());
    }

    @Test
    void addingTheMissingPutPasses() {
        Outcome outcome = level.run("""
            Thread producer = Thread.ofVirtual().start(() -> {
                queue.put(1);
                queue.put(2);
                queue.put(3);
                queue.put(4);
                queue.put(5);
            });

            Thread consumer = Thread.ofVirtual().start(() -> {
                int x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
            });
            """);

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void droppingTheLastTakeAlsoPassesIfTotalsMatch() {
        // Removing the fifth take avoids the deadlock, but the total only
        // reaches 10 — the validator catches the wrong sum.
        Outcome outcome = level.run("""
            Thread producer = Thread.ofVirtual().start(() -> {
                queue.put(1);
                queue.put(2);
                queue.put(3);
                queue.put(4);
            });

            Thread consumer = Thread.ofVirtual().start(() -> {
                int x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
                x = queue.take();
                totalReceived = totalReceived + x;
            });
            """);

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().contains("expected 15"));
    }

    @Test
    void putOnNonQueueIsRejected() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {
                totalReceived.put(1);
            });
            """);

        assertFalse(outcome.passed());
        assertTrue(outcome.hasErrors());
    }

    @Test
    void fullSourceImportsArrayBlockingQueue() {
        String full = level.fullSourceWith(level.starterCode());
        assertTrue(full.contains("import java.util.concurrent.ArrayBlockingQueue"));
        assertTrue(full.contains("static final ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<>(3)"));
    }
}
