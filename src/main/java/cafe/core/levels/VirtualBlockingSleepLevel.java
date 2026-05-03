package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

public final class VirtualBlockingSleepLevel extends AbstractLevel {

    private static final int EXPECTED_TOTAL = 15;
    private static final String TOTAL = "totalReceived";

    public VirtualBlockingSleepLevel() {
        super(LevelSpec.builder()
            .id("virtual-blocking-sleep")
            .title("Park the Waiting Work")
            .chapter("Chapter III · Modern Java")
            .lessonClassName("VirtualBlockingSleepLesson")
            .passingCondition("totalReceived equals 15")
            .declare("receipts", new SharedType.QueueType(2))
            .declare("totalReceived", new SharedType.IntType(0))
            .declare("platformPool", new SharedType.FixedExecutorType(2))
            .declare("virtualPool", new SharedType.VirtualExecutorType())
            .intro("""
                The kitchen has five slow calls. Each call waits, then puts a
                receipt into a small queue.

                platformPool only has two platform workers. Once the receipts
                queue fills, the next workers block trying to put more receipts —
                still holding their pool slots. The collector is still waiting
                for a platform worker, so nobody drains the queue. Pool
                exhausted, kitchen frozen.

                Move the slow blocking tasks to virtualPool. Let the collector
                stay on platformPool.
                """)
            .starterCode("""
                // platformPool: fixed pool with 2 platform workers.
                // virtualPool: one virtual thread per submitted task.
                //
                // Each slow task sleeps, then writes one receipt.
                //
                // Run as-is: platform workers eventually block on receipts.put(...)
                // while still holding their pool slots. The collector can't start.
                //
                // Fix: move the five slow tasks to virtualPool.

                platformPool.submit(() -> {
                    Thread.sleep(250);
                    receipts.put(1);
                });

                platformPool.submit(() -> {
                    Thread.sleep(250);
                    receipts.put(2);
                });

                platformPool.submit(() -> {
                    Thread.sleep(250);
                    receipts.put(3);
                });

                platformPool.submit(() -> {
                    Thread.sleep(250);
                    receipts.put(4);
                });

                platformPool.submit(() -> {
                    Thread.sleep(250);
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
            .hint("The five tasks that call Thread.sleep(...) are the blocking tasks. Those are the ones that should move to virtualPool.")
            .hint("Move the five slow tasks to virtualPool.submit(...). Leave the collector on platformPool.")
            .hint("Virtual threads help when many tasks wait. They do not make CPU-heavy work faster.")
            .resultPrintln("\"Total received: \" + totalReceived")
            .build());
    }

    @Override
    public Outcome validate(SimulationResult sim) {
        if (sim.hasError()) {
            return haltedOnError(sim);
        }
        int total = sim.finalGlobals().getOrDefault(TOTAL, 0);
        if (total == EXPECTED_TOTAL) {
            return pass(
                "Total received: " + total + ". Blocking tasks parked without consuming the platform pool.",
                sim
            );
        }
        return fail("Total received: " + total + ", expected " + EXPECTED_TOTAL + ".", sim);
    }
}
