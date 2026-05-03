package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

public final class VirtualVsPlatformLevel extends AbstractLevel {

    private static final int EXPECTED_SUM = 15;
    private static final int DEADLINE_MILLIS = 900;
    private static final String TOTAL = "totalReceived";

    public VirtualVsPlatformLevel() {
        super(LevelSpec.builder()
                .id("virtual-vs-platform")
                .title("Virtual vs Platform Threads")
                .chapter("Chapter III · Modern Java")
                .lessonClassName("VirtualVsPlatformLesson")
                .passingCondition("Total received equals 15 before the deadline")

                .declare("receipts", new SharedType.QueueType(5))
                .declare("totalReceived", new SharedType.IntType(0))
                .declare("platformPool", new SharedType.FixedExecutorType(2))
                .declare("virtualPool", new SharedType.VirtualExecutorType())

                .intro("""
                The kitchen has five slow calls to make. Each one waits for
                half a second, like a network call, database query, or remote
                service request.

                platformPool has only two platform threads. If all five slow
                calls run there, only two can sleep at a time. The work finishes
                in waves: two calls, two calls, one call.

                virtualPool starts one virtual thread per task. All five calls
                can wait at the same time, because sleeping virtual threads do
                not monopolize scarce platform-pool workers.

                Move the slow blocking tasks from platformPool to virtualPool.
                """)

                .starterCode("""
                // platformPool: fixed pool with 2 platform workers.
                // virtualPool: one virtual thread per submitted task.
                //
                // Each slow call waits 500 ms, then returns a number.
                //
                // Run as-is: platformPool can only run two sleepers at once.
                // Fix: move the five slow calls to virtualPool.

                platformPool.submit(() -> {
                    Thread.sleep(500);
                    receipts.put(1);
                });

                platformPool.submit(() -> {
                    Thread.sleep(500);
                    receipts.put(2);
                });

                platformPool.submit(() -> {
                    Thread.sleep(500);
                    receipts.put(3);
                });

                platformPool.submit(() -> {
                    Thread.sleep(500);
                    receipts.put(4);
                });

                platformPool.submit(() -> {
                    Thread.sleep(500);
                    receipts.put(5);
                });

                platformPool.submit(() -> {
                    int x = receipts.take();
                    totalReceived = totalReceived + x;

                    x = receipts.take();
                    totalReceived = totalReceived + x;

                    x = receipts.take();
                    totalReceived = totalReceived + x;

                    x = receipts.take();
                    totalReceived = totalReceived + x;

                    x = receipts.take();
                    totalReceived = totalReceived + x;
                });
                """)

                .hint("The five tasks that call Thread.sleep(500) are the blocking tasks. Those are the ones that should move to virtualPool.")
                .hint("Leave the collector on platformPool. The collector is not the problem; the sleepers are.")
                .hint("Virtual threads help when many tasks are waiting. They do not make CPU-heavy code faster.")

                .resultPrintln("\"Total received: \" + totalReceived")
                .build());
    }

    @Override
    public Outcome validate(SimulationResult sim) {
        if (sim.hasError()) {
            return haltedOnError(sim);
        }

        int total = sim.finalGlobals().getOrDefault(TOTAL, 0);

        // Replace elapsedMillis() with whatever timing API your simulator exposes.
        long elapsed = sim.elapsedMillis();

        if (total == EXPECTED_SUM && elapsed <= DEADLINE_MILLIS) {
            return pass(
                    "Total received: " + total + " in " + elapsed + " ms. The blocking work ran concurrently on virtual threads.",
                    sim
            );
        }

        if (total == EXPECTED_SUM) {
            return fail(
                    "Total received: " + total + ", but it took " + elapsed + " ms. The slow blocking tasks still ran in waves on the platform pool.",
                    sim
            );
        }

        return fail("Total received: " + total + ", expected " + EXPECTED_SUM + ".", sim);
    }
}