package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

public final class LockOrderingLevel extends AbstractLevel {

    private static final int EXPECTED_MEALS = 2;
    private static final String MEALS = "meals";

    public LockOrderingLevel() {
        super(LevelSpec.builder()
            .id("lock-ordering")
            .title("Lock in One Order")
            .chapter("Chapter II · Locks & coordination")
            .lessonClassName("LockOrderingLesson")
            .passingCondition("meals ends at 2")
            .declare("meals", new SharedType.IntType(0))
            .declare("knife", new SharedType.LockType())
            .declare("pan", new SharedType.LockType())
            .intro("""
                Two chefs need the same two tools. One takes the knife first
                then the pan; the other takes the pan first then the knife.
                That creates a cycle — each chef holds one lock while waiting
                forever for the other.
                """)
            .starterCode("""
                Thread chef1 = Thread.ofVirtual().start(() -> {
                    knife.lock();
                    pan.lock();

                    meals++;

                    pan.unlock();
                    knife.unlock();
                });

                Thread chef2 = Thread.ofVirtual().start(() -> {
                    pan.lock();
                    knife.lock();

                    meals++;

                    knife.unlock();
                    pan.unlock();
                });
                """)
            .hint("Deadlock needs a cycle. Break the cycle by using one global lock order.")
            .hint("Both chefs should acquire knife before pan, or both should acquire pan before knife.")
            .hint("The unlock order can be reversed, but the lock acquisition order must match.")
            .resultPrintln("\"Meals: \" + meals")
            .build());
    }

    @Override
    public Outcome validate(SimulationResult sim) {
        if (sim.hasError()) {
            return haltedOnError(sim);
        }
        int meals = sim.finalGlobals().getOrDefault(MEALS, 0);
        if (meals == EXPECTED_MEALS) {
            return pass("Meals: " + meals + ". Both chefs used the same lock order.", sim);
        }
        return fail("Meals: " + meals + ", expected " + EXPECTED_MEALS + ".", sim);
    }
}
