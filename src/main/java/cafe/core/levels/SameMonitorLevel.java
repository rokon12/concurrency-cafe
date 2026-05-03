package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

public final class SameMonitorLevel extends AbstractLevel {

    private static final int EXPECTED_PLATES = 4;
    private static final String PLATES = "plates";

    public SameMonitorLevel() {
        super(LevelSpec.builder()
            .id("same-monitor")
            .title("Same Lock, Same State")
            .chapter("Chapter II · Locks & coordination")
            .lessonClassName("SameMonitorLesson")
            .passingCondition("plates ends at 4")
            .declare("plates", new SharedType.IntType(0))
            .declare("prepStation", new SharedType.MonitorType())
            .declare("passWindow", new SharedType.MonitorType())
            .intro("""
                Both chefs use synchronized blocks, but they lock different
                monitors. That means they aren't actually excluding each other.
                A lock only protects state when every thread uses the same lock
                for that state.
                """)
            .starterCode("""
                Thread chef1 = Thread.ofVirtual().start(() -> {
                    synchronized (prepStation) {
                        int x = plates;
                        plates = x + 1;

                        x = plates;
                        plates = x + 1;
                    }
                });

                Thread chef2 = Thread.ofVirtual().start(() -> {
                    synchronized (passWindow) {
                        int x = plates;
                        plates = x + 1;

                        x = plates;
                        plates = x + 1;
                    }
                });
                """)
            .hint("Both synchronized blocks are real, but they don't talk to each other.")
            .hint("plates has one guard. Pick prepStation or passWindow, then use it everywhere plates is changed.")
            .hint("The bug is lock identity, not the size of the synchronized block.")
            .resultPrintln("\"Plates: \" + plates")
            .build());
    }

    @Override
    public Outcome validate(SimulationResult sim) {
        if (sim.hasError()) {
            return haltedOnError(sim);
        }
        int plates = sim.finalGlobals().getOrDefault(PLATES, 0);
        if (plates == EXPECTED_PLATES) {
            return pass("Plates: " + plates + ". The shared state used one shared guard.", sim);
        }
        return fail("Plates: " + plates + ", expected " + EXPECTED_PLATES + ".", sim);
    }
}
