package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

public final class ProducerConsumerLevel extends AbstractLevel {

    private static final int EXPECTED_SUM = 15;
    private static final String TOTAL = "totalReceived";

    public ProducerConsumerLevel() {
        super(LevelSpec.builder()
            .id("producer-consumer")
            .title("Producer / Consumer")
            .chapter("Chapter II · Locks & coordination")
            .lessonClassName("ProducerConsumerLesson")
            .passingCondition("Total received equals 15 (orders 1..5)")
            .declare("queue", new SharedType.QueueType(3))
            .declare("totalReceived", new SharedType.IntType(0))
            .intro("""
                Five orders should flow from the producer into the queue and out
                through the consumer, summing to 15. Run the starter and watch the
                kitchen freeze. queue.take() blocks INDEFINITELY when the queue is
                empty — if the producer never sends, the consumer waits forever.
                Find what's missing.
                """)
            .starterCode("""
                // Five orders (1..5) should pass through the queue. Target: 15.
                // The kitchen freezes — there's an order the producer never sends.

                Thread producer = Thread.ofVirtual().start(() -> {
                    queue.put(1);
                    queue.put(2);
                    queue.put(3);
                    queue.put(4);
                    // The fifth order never goes out.
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
                """)
            .hint("queue.take() returns nothing while the queue is empty — the consumer parks forever waiting for the fifth order.")
            .hint("Count the producer's put calls, then count the consumer's takes. They have to match.")
            .hint("In real Java you'd guard against this with a sentinel value, queue.poll(timeout, ...), or a try/finally that signals completion. Our DSL keeps it simple — just match the counts.")
            .resultPrintln("\"Total received: \" + totalReceived")
            .build());
    }

    @Override
    public Outcome validate(SimulationResult sim) {
        if (sim.hasError()) {
            return haltedOnError(sim);
        }
        int total = sim.finalGlobals().getOrDefault(TOTAL, 0);
        if (total == EXPECTED_SUM) {
            return pass("Total received: " + total + ". Every order delivered.", sim);
        }
        return fail("Total received: " + total + ", expected " + EXPECTED_SUM + ".", sim);
    }
}
