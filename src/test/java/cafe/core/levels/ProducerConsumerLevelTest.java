package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProducerConsumerLevelTest {

    private final ProducerConsumerLevel level = new ProducerConsumerLevel();

    @Test
    void starterCodePassesBecauseItIsTheCanonicalSolution() {
        Outcome outcome = level.run(level.starterCode());

        assertTrue(outcome.passed(), outcome.summary());
    }

    @Test
    void consumerWithoutTakingFailsToReachTotal() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {
                queue.put(1);
                queue.put(2);
                queue.put(3);
            });
            Thread.ofVirtual().start(() -> {
                int x = queue.take();
                totalReceived = totalReceived + x;
            });
            """);

        assertFalse(outcome.passed());
    }

    @Test
    void producerWithoutConsumerDeadlocksWhenQueueFills() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> {
                queue.put(1);
                queue.put(2);
                queue.put(3);
                queue.put(4);
            });
            """);

        assertFalse(outcome.passed());
        assertTrue(outcome.summary().toLowerCase().contains("deadlock")
                || outcome.summary().toLowerCase().contains("halt"),
            "expected halt/deadlock when single producer fills queue past capacity, got: "
                + outcome.summary());
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
