package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

public final class WaitNotifyLevel extends AbstractLevel {

    private static final String RESULT = "result";
    private static final int EXPECTED = 42;

    public WaitNotifyLevel() {
        super(LevelSpec.builder()
            .id("wait-notify")
            .title("Wait / Notify")
            .chapter("Chapter II · Locks & coordination")
            .lessonClassName("WaitNotifyLesson")
            .passingCondition("result equals 42")
            .declare("data", new SharedType.IntType(0))
            .declare("result", new SharedType.IntType(0))
            .declare("lock", new SharedType.MonitorType())
            .intro("""
                The producer prepares data; the consumer reads it into result.
                The consumer is declared first — without coordination it grabs
                the monitor, reads 0, and finishes before the producer ever
                writes.

                Use the monitor primitives that BlockingQueue is built on:
                lock.wait() releases the monitor and parks the chef; another
                chef calls lock.notify() to wake it up. Both must be called
                while holding the monitor.
                """)
            .starterCode("""
                // Consumer races ahead of the producer. result lands at 0.
                // Coordinate with lock.wait() / lock.notify() so the consumer
                // parks until the producer signals.

                Thread consumer = Thread.ofVirtual().start(() -> {
                    synchronized (lock) {
                        result = data;
                    }
                });

                Thread producer = Thread.ofVirtual().start(() -> {
                    synchronized (lock) {
                        data = 42;
                    }
                });
                """)
            .hint("Inside synchronized, lock.wait() releases the monitor and blocks until another chef calls lock.notify() (or notifyAll()) on the same monitor.")
            .hint("Consumer should wait BEFORE reading data. Producer should notify AFTER setting data.")
            .hint("After the producer's synchronized block exits, the consumer's wait() returns — it re-acquires the monitor, then continues past the wait line.")
            .resultPrintln("\"result: \" + result")
            .build());
    }

    @Override
    public Outcome validate(SimulationResult sim) {
        if (sim.hasError()) {
            return haltedOnError(sim);
        }
        int result = sim.finalGlobals().getOrDefault(RESULT, 0);
        if (result == EXPECTED) {
            return pass("result = " + result + ". Consumer waited until the producer signaled.", sim);
        }
        return fail("result = " + result + ", expected " + EXPECTED
            + ". The consumer needs to wait for the producer's signal.", sim);
    }
}
