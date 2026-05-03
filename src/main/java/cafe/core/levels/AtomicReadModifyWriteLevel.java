package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

public final class AtomicReadModifyWriteLevel extends AbstractLevel {

    private static final int EXPECTED_ORDERS = 6;
    private static final String ORDERS = "orders";

    public AtomicReadModifyWriteLevel() {
        super(LevelSpec.builder()
            .id("atomic-read-modify-write")
            .title("Atomic Means One Operation")
            .chapter("Chapter I · Shared state")
            .lessonClassName("AtomicReadModifyWriteLesson")
            .passingCondition("orders ends at 6")
            .declare("orders", new SharedType.AtomicIntegerType(0))
            .intro("""
                AtomicInteger gives you atomic operations, but it does not make
                a whole sequence atomic. get() followed by set() is still a
                read-modify-write race. Use one atomic operation for the whole
                update.
                """)
            .starterCode("""
                Thread chef1 = Thread.ofVirtual().start(() -> {
                    int x = orders.get();
                    orders.set(x + 1);

                    x = orders.get();
                    orders.set(x + 1);

                    x = orders.get();
                    orders.set(x + 1);
                });

                Thread chef2 = Thread.ofVirtual().start(() -> {
                    int x = orders.get();
                    orders.set(x + 1);

                    x = orders.get();
                    orders.set(x + 1);

                    x = orders.get();
                    orders.set(x + 1);
                });
                """)
            .hint("orders.get() is atomic. orders.set(...) is atomic. The pair is not atomic.")
            .hint("Use orders.incrementAndGet() for each order.")
            .hint("The fix is not synchronized here. The point is to use the atomic operation directly.")
            .resultPrintln("\"Orders: \" + orders.get()")
            .build());
    }

    @Override
    public Outcome validate(SimulationResult sim) {
        if (sim.hasError()) {
            return haltedOnError(sim);
        }
        int orders = sim.finalGlobals().getOrDefault(ORDERS, 0);
        if (orders == EXPECTED_ORDERS) {
            return pass("Orders: " + orders + ". Each increment happened atomically.", sim);
        }
        return fail("Orders: " + orders + ", expected " + EXPECTED_ORDERS + ".", sim);
    }
}
