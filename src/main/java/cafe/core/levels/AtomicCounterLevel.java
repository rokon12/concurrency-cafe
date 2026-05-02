package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

public final class AtomicCounterLevel extends AbstractLevel {

    private static final int EXPECTED = 43;
    private static final String COUNTER = "counter";

    public AtomicCounterLevel() {
        super(LevelSpec.builder()
            .id("atomic-counter")
            .title("Atomic Counter")
            .chapter("Chapter I · Shared state")
            .lessonClassName("AtomicCounterLesson")
            .passingCondition("Counter ends at 43")
            .declare("counter", new SharedType.AtomicIntegerType(41))
            .intro("""
                Same scenario as Level 1, but the kitchen now uses an AtomicInteger.
                With the right primitive, you don't need an external lock. Find it.
                """)
            .starterCode("""
                // counter is an AtomicInteger that starts at 41.
                // Each chef must safely increment it once. The target is 43.

                Thread chef1 = Thread.ofVirtual().start(() -> {
                    // Add the call that atomically increments counter.
                });

                Thread chef2 = Thread.ofVirtual().start(() -> {
                    // Same here.
                });
                """)
            .hint("AtomicInteger.incrementAndGet() does the read+add+write as a single atomic step.")
            .hint("Each chef should call counter.incrementAndGet(); once.")
            .hint("Notice that int operations like '++' or '=' don't apply here — counter is an AtomicInteger, not an int.")
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
            return pass("Counter ended at " + actual + " — atomic ops, no lock needed.", sim);
        }
        return fail("Counter ended at " + actual + ", expected " + EXPECTED + ".", sim);
    }
}
