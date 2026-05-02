package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

import java.util.List;

public final class DeadlockKitchenLevel extends AbstractLevel {

    public DeadlockKitchenLevel() {
        super(LevelSpec.builder()
            .id("deadlock-kitchen")
            .title("Level 3: Deadlock Kitchen")
            .chapter("Chapter II · Locks & coordination")
            .lessonClassName("DeadlockKitchen")
            .passingCondition("Both chefs plate a dish — no deadlock")
            .declare("oven", new SharedType.MonitorType())
            .declare("fryer", new SharedType.MonitorType())
            .intro("""
                Both chefs need the oven AND the fryer to plate a dish.
                The starter code deadlocks. Fix it so both chefs finish.
                """)
            .starterCode("""
                // Each chef needs both the oven and the fryer to plate a dish.
                // Run this as-is and watch what happens. Then make both chefs finish.

                Thread chef1 = Thread.ofVirtual().start(() -> {
                    synchronized (oven) {
                        synchronized (fryer) {
                            System.out.println("chef1 plates a dish");
                        }
                    }
                });

                Thread chef2 = Thread.ofVirtual().start(() -> {
                    synchronized (fryer) {
                        synchronized (oven) {
                            System.out.println("chef2 plates a dish");
                        }
                    }
                });
                """)
            .hint("chef1 grabs the oven, chef2 grabs the fryer — then each waits forever for the other.")
            .hint("Pick a fixed order, say oven first then fryer, and make every chef follow it.")
            .hint("Consistent lock ordering across all threads prevents the cycle.")
            .build());
    }

    @Override
    public Outcome validate(SimulationResult sim) {
        if (sim.hasError()) {
            return haltedOnError(sim);
        }
        int ovenAcquires = countAcquires(sim.events(), "oven");
        int fryerAcquires = countAcquires(sim.events(), "fryer");
        if (ovenAcquires < 2 || fryerAcquires < 2) {
            return fail(
                "Each chef must cook with BOTH the oven and the fryer (current run: oven acquired "
                    + ovenAcquires + " time(s), fryer " + fryerAcquires + " time(s)).",
                sim
            );
        }
        return pass("Both chefs plated their dishes — no deadlock.", sim);
    }

    private static int countAcquires(List<String> events, String lockName) {
        String needle = "acquires lock '" + lockName + "'";
        int count = 0;
        for (String event : events) {
            if (event.contains(needle)) count++;
        }
        return count;
    }
}
