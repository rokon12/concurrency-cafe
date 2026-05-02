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
            .declare("fixedPool", new SharedType.FixedExecutorType(1))
            .declare("virtualPool", new SharedType.VirtualExecutorType())
            .intro("""
                Producer/consumer cooperation, but the kitchen runs tasks through a
                fixed thread pool of size 1. fixedPool only has one slot — the
                producer takes it, fills the queue, then parks waiting for room.
                The consumer can never start because the slot is occupied. Whole
                kitchen freezes.

                Java 21 ships another executor — virtualPool, backed by virtual
                threads — that doesn't bind tasks to OS threads. Submit there
                instead.
                """)
            .starterCode("""
                // fixedPool: Executors.newFixedThreadPool(1) — one slot.
                // virtualPool: Executors.newVirtualThreadPerTaskExecutor() — unbounded.
                //
                // Run as-is and watch fixedPool starve. Then move the submits.

                fixedPool.submit(() -> {
                    queue.put(1);
                    queue.put(2);
                    queue.put(3);
                    queue.put(4);
                    queue.put(5);
                });

                fixedPool.submit(() -> {
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
            .hint("A fixed pool task holds its slot for the entire body — even when it's parked waiting on a queue. Pool=1 means no concurrent submits.")
            .hint("Switch fixedPool.submit(...) to virtualPool.submit(...) — at least for one of the two tasks. Virtual threads park without holding an OS thread.")
            .hint("In production Java this is the actual fix: replace `Executors.newFixedThreadPool(N)` with `Executors.newVirtualThreadPerTaskExecutor()` for I/O-heavy work.")
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
            return pass("Total received: " + total + ". Tasks ran without competing for an OS thread.", sim);
        }
        return fail("Total received: " + total + ", expected " + EXPECTED_SUM + ".", sim);
    }
}
