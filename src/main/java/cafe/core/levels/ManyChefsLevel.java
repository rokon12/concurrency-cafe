package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

public final class ManyChefsLevel extends AbstractLevel {

    private static final int CHEF_COUNT = 4;
    private static final int EXPECTED = CHEF_COUNT;
    private static final String COUNTER = "counter";

    public ManyChefsLevel() {
        super(LevelSpec.builder()
            .id("many-chefs")
            .title("Many Chefs, One Counter")
            .chapter("Chapter I · Shared state")
            .lessonClassName("ManyChefsLesson")
            .passingCondition("Counter ends at 4")
            .declare("counter", new SharedType.IntType(0))
            .declare("counterLock", new SharedType.MonitorType())
            .intro("""
                Same race as Lost Update, but now four chefs share the counter.
                Without protection the bug gets worse — the counter ends at 1, not 4.
                Pick a fix that scales: synchronized blocks, AtomicInteger, or the
                explicit Lock pattern from earlier levels.
                """)
            .starterCode("""
                // Four chefs, one shared counter starting at 0. Target: 4.
                // The race scales — without a fix the counter lands at 1.

                Thread chef1 = Thread.ofVirtual().start(() -> {
                    int x = counter;
                    counter = x + 1;
                });

                Thread chef2 = Thread.ofVirtual().start(() -> {
                    int x = counter;
                    counter = x + 1;
                });

                Thread chef3 = Thread.ofVirtual().start(() -> {
                    int x = counter;
                    counter = x + 1;
                });

                Thread chef4 = Thread.ofVirtual().start(() -> {
                    int x = counter;
                    counter = x + 1;
                });
                """)
            .hint("All four chefs read counter = 0 before any of them writes back. Only one increment survives.")
            .hint("The fix from Lost Update — wrap the read+write in `synchronized (counterLock) { ... }` — works for any number of chefs.")
            .hint("Locks scale, but they serialize the work. Atomic primitives like incrementAndGet would let chefs proceed in parallel — that's a different level's lesson.")
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
            return pass("Counter ended at " + actual + ". All " + CHEF_COUNT + " increments stuck.", sim);
        }
        int lost = EXPECTED - actual;
        return fail(
            "Counter ended at " + actual + ", expected " + EXPECTED
                + " (" + lost + " update" + (lost == 1 ? "" : "s") + " lost).",
            sim
        );
    }
}
