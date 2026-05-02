package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

public final class ReentrantLockLevel extends AbstractLevel {

    private static final int EXPECTED = 2;
    private static final String COUNTER = "counter";

    public ReentrantLockLevel() {
        super(LevelSpec.builder()
            .id("reentrant-lock")
            .title("Explicit Lock")
            .chapter("Chapter II · Locks & coordination")
            .lessonClassName("ExplicitLockLesson")
            .passingCondition("Counter ends at 2")
            .declare("counter", new SharedType.IntType(0))
            .declare("counterLock", new SharedType.LockType())
            .intro("""
                Same lost-update bug as Level 1, but counterLock is a ReentrantLock now.
                The synchronized keyword needs an Object monitor — the parser will reject
                it here. Use the explicit Lock API.
                """)
            .starterCode("""
                // counter starts at 0. Each chef should add 1, target = 2.
                // counterLock is a ReentrantLock — synchronized won't compile against it.

                Thread chef1 = Thread.ofVirtual().start(() -> {
                    int x = counter;
                    counter = x + 1;
                });

                Thread chef2 = Thread.ofVirtual().start(() -> {
                    int x = counter;
                    counter = x + 1;
                });
                """)
            .hint("ReentrantLock has explicit .lock() and .unlock() methods — no keyword required.")
            .hint("Wrap the read+write with counterLock.lock(); … counterLock.unlock();")
            .hint("In real Java you'd use try { … } finally { unlock(); } so an exception still releases the lock — our DSL skips that detail for now.")
            .resultPrintln("\"Final counter: \" + counter")
            .build());
    }

    @Override
    public Outcome validate(SimulationResult sim) {
        if (sim.hasError()) {
            return haltedOnError(sim);
        }
        int actual = sim.finalGlobals().getOrDefault(COUNTER, 0);
        if (actual == EXPECTED) {
            return pass("Counter ended at " + actual + ". The lock held the line.", sim);
        }
        int lost = EXPECTED - actual;
        return fail(
            "Counter ended at " + actual + ", expected " + EXPECTED
                + " (" + lost + " update" + (lost == 1 ? "" : "s") + " lost).",
            sim
        );
    }
}
