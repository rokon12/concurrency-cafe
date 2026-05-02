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
            .passingCondition("Total received equals 15 (1+2+3+4+5)")
            .declare("queue", new SharedType.QueueType(3))
            .declare("totalReceived", new SharedType.IntType(0))
            .intro("""
                A back-of-house queue with capacity 3. The producer puts orders 1..5,
                the consumer takes them and sums into totalReceived. The queue is
                smaller than the order count, so put blocks when full and take blocks
                when empty — that's the cooperation pattern.
                """)
            .starterCode("""
                // queue is an ArrayBlockingQueue<Integer>(3). put blocks if full;
                // take blocks if empty. Move all 5 items through to totalReceived.

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
                """)
            .hint("BlockingQueue.put waits when the queue is full; BlockingQueue.take waits when the queue is empty.")
            .hint("The queue holds at most 3 — the producer parks until the consumer makes room. The simulator handles the parking automatically.")
            .hint("Target: 1 + 2 + 3 + 4 + 5 = 15. Take all five items into totalReceived.")
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
