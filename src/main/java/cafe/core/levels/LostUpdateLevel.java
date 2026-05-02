package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

public final class LostUpdateLevel extends AbstractLevel {

    private static final int EXPECTED = 43;
    private static final String COUNTER = "counter";

    public LostUpdateLevel() {
        super(LevelSpec.builder()
            .id("lost-update")
            .title("Lost Update")
            .chapter("Chapter I · Shared state")
            .lessonClassName("LostUpdateLesson")
            .passingCondition("Counter ends at 43")
            .declare("counter", new SharedType.IntType(41))
            .declare("counterLock", new SharedType.MonitorType())
            .intro("""
                Two chefs increment counter. It starts at 41 and should end at 43.
                Run the starter code first to see the bug, then fix it so no update is lost.
                """)
            .starterCode("""
                // counter starts at 41. Each chef serves one more order.
                // Run this and watch what happens. Then make both increments stick.

                Thread chef1 = Thread.ofVirtual().start(() -> {
                    int x = counter;
                    counter = x + 1;
                });

                Thread chef2 = Thread.ofVirtual().start(() -> {
                    int x = counter;
                    counter = x + 1;
                });
                """)
            .hint("Both chefs read counter before either writes back. The second write overwrites the first.")
            .hint("Wrap the read+write in `synchronized (counterLock) { ... }` so only one chef is inside at a time.")
            .hint("Manual locking also works: declare a ReentrantLock-shaped variable and call `.lock()` / `.unlock()`. (For now, this level only ships counterLock as an Object monitor — synchronized is the path here.)")
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
            return pass("Counter ended at " + actual + ". Both increments stuck.", sim);
        }
        int lost = EXPECTED - actual;
        return fail(
            "Counter ended at " + actual + ", expected " + EXPECTED
                + " (" + lost + " update" + (lost == 1 ? "" : "s") + " lost).",
            sim
        );
    }
}
