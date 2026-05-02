package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

public final class AtomicAddLevel extends AbstractLevel {

    private static final int EXPECTED = 10;
    private static final String COUNTER = "counter";

    public AtomicAddLevel() {
        super(LevelSpec.builder()
            .id("atomic-add")
            .title("Atomic Add")
            .chapter("Chapter I · Shared state")
            .lessonClassName("AtomicAddLesson")
            .passingCondition("Counter ends at 10")
            .declare("counter", new SharedType.AtomicIntegerType(0))
            .intro("""
                counter is an AtomicInteger starting at 0. Each chef must atomically
                add 5 to it. Target: 10. incrementAndGet() only adds 1 — what's the
                right call for adding more?
                """)
            .starterCode("""
                // counter is an AtomicInteger starting at 0.
                // Each chef must atomically add 5. Target: 10.

                Thread chef1 = Thread.ofVirtual().start(() -> {
                    // Atomically add 5 to counter.
                });

                Thread chef2 = Thread.ofVirtual().start(() -> {
                    // Same here.
                });
                """)
            .hint("AtomicInteger has more than incrementAndGet — there's also addAndGet(n) for arbitrary deltas.")
            .hint("Each chef should call counter.addAndGet(5);")
            .hint("getAndAdd(5) works too — they differ only in what they return; the side effect is the same.")
            .resultPrintln("\"Final counter: \" + counter.get()")
            .build());
    }

    @Override
    public Outcome validate(SimulationResult sim) {
        if (sim.hasError()) {
            return haltedOnError(sim);
        }
        int actual = sim.finalGlobals().getOrDefault(COUNTER, 0);
        if (actual == EXPECTED) {
            return pass("Counter ended at " + actual + ". Both adds landed atomically.", sim);
        }
        return fail("Counter ended at " + actual + ", expected " + EXPECTED + ".", sim);
    }
}
