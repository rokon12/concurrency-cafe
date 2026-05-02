package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

public final class VirtualVsPlatformLevel extends AbstractLevel {

    private static final int EXPECTED_SUM = 15;
    private static final String TOTAL = "totalReceived";

    public VirtualVsPlatformLevel() {
        super(LevelSpec.builder()
            .id("virtual-vs-platform")
            .title("Virtual vs Platform")
            .chapter("Chapter III · Modern Java")
            .lessonClassName("VirtualVsPlatformLesson")
            .passingCondition("Total received equals 15")
            .declare("queue", new SharedType.QueueType(2))
            .declare("totalReceived", new SharedType.IntType(0))
            .intro("""
                Same producer/consumer scenario, but the kitchen has only one platform
                thread slot to share between them. The starter uses Thread.ofPlatform()
                for both — the producer takes the slot, fills the queue, and blocks. The
                consumer never gets a slot. Classic platform-pool deadlock. Java 21+
                gives you a way out.
                """)
            .starterCode("""
                // The platform thread pool is size 1. Both chefs start as platform threads.
                // Producer takes the slot, fills the queue, then blocks on full. Consumer
                // can't start (no slot). The whole kitchen freezes.
                //
                // Hint: Java 21 has thread bodies that don't compete for OS threads.

                Thread producer = Thread.ofPlatform().start(() -> {
                    queue.put(1);
                    queue.put(2);
                    queue.put(3);
                    queue.put(4);
                    queue.put(5);
                });

                Thread consumer = Thread.ofPlatform().start(() -> {
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
            .hint("Platform threads occupy a fixed pool slot until they're done. Pool=1 means only one chef can ever be running.")
            .hint("When the producer parks waiting for queue space, it's still holding the slot. The consumer can't even start to drain the queue.")
            .hint("Switch Thread.ofPlatform().start(...) to Thread.ofVirtual().start(...). Virtual threads park without holding an OS thread.")
            .resultPrintln("\"Total received: \" + totalReceived")
            .build());
    }

    @Override
    public int platformPoolSize() {
        return 1;
    }

    @Override
    public Outcome validate(SimulationResult sim) {
        if (sim.hasError()) {
            return haltedOnError(sim);
        }
        int total = sim.finalGlobals().getOrDefault(TOTAL, 0);
        if (total == EXPECTED_SUM) {
            return pass("Total received: " + total + ". Both chefs ran without competing for an OS thread.", sim);
        }
        return fail("Total received: " + total + ", expected " + EXPECTED_SUM + ".", sim);
    }
}
