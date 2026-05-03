package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

public final class MonitorCounterLevel extends AbstractLevel {

    private static final int EXPECTED_SERVINGS = 6;
    private static final String SERVINGS = "servings";

    public MonitorCounterLevel() {
        super(LevelSpec.builder()
            .id("monitor-counter")
            .title("Guard the Counter")
            .chapter("Chapter I · Shared state")
            .lessonClassName("MonitorCounterLesson")
            .passingCondition("servings ends at 6")
            .declare("servings", new SharedType.IntType(0))
            .declare("register", new SharedType.MonitorType())
            .intro("""
                Two chefs write to the same counter. Each update reads the old
                value, computes a new value, then stores it back. Without a
                shared monitor those steps overlap and one chef's work erases
                the other's.
                """)
            .starterCode("""
                Thread chef1 = Thread.ofVirtual().start(() -> {
                    int x = servings;
                    servings = x + 1;

                    x = servings;
                    servings = x + 1;

                    x = servings;
                    servings = x + 1;
                });

                Thread chef2 = Thread.ofVirtual().start(() -> {
                    int x = servings;
                    servings = x + 1;

                    x = servings;
                    servings = x + 1;

                    x = servings;
                    servings = x + 1;
                });
                """)
            .hint("The race isn't just on the assignment. The read and write together are the critical section.")
            .hint("Use synchronized (register) around the updates that touch servings.")
            .hint("Both chefs must synchronize on the same monitor object.")
            .resultPrintln("\"Servings: \" + servings")
            .build());
    }

    @Override
    public Outcome validate(SimulationResult sim) {
        if (sim.hasError()) {
            return haltedOnError(sim);
        }
        int servings = sim.finalGlobals().getOrDefault(SERVINGS, 0);
        if (servings == EXPECTED_SERVINGS) {
            return pass("Servings: " + servings + ". The shared counter was guarded.", sim);
        }
        return fail("Servings: " + servings + ", expected " + EXPECTED_SERVINGS + ".", sim);
    }
}
