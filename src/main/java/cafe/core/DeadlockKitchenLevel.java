package cafe.core;

import cafe.core.dsl.ParseException;
import cafe.core.dsl.Parser;
import cafe.core.dsl.Program;
import cafe.core.sim.SimulationException;
import cafe.core.sim.SimulationResult;
import cafe.core.sim.Simulator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeadlockKitchenLevel implements Level {

    private static final Map<String, SharedType> DECLARATIONS = Map.of(
        "oven", new SharedType.MonitorType(),
        "fryer", new SharedType.MonitorType()
    );

    private static final String STARTER = """
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
        """;

    private static final List<String> HINTS = List.of(
        "chef1 grabs the oven, chef2 grabs the fryer — then each waits forever for the other.",
        "Pick a fixed order, say oven first then fryer, and make every chef follow it.",
        "Consistent lock ordering across all threads prevents the cycle."
    );

    @Override
    public String id() {
        return "deadlock-kitchen";
    }

    @Override
    public String title() {
        return "Level 3: Deadlock Kitchen";
    }

    @Override
    public String intro() {
        return """
            Both chefs need the oven AND the fryer to plate a dish.
            The starter code deadlocks. Fix it so both chefs finish.
            """;
    }

    @Override
    public String starterCode() {
        return STARTER;
    }

    @Override
    public List<String> hints() {
        return HINTS;
    }

    @Override
    public Map<String, SharedType> sharedDeclarations() {
        return DECLARATIONS;
    }

    @Override
    public String fullSourceWith(String userCode) {
        StringBuilder sb = new StringBuilder();
        sb.append("public class DeadlockKitchen {\n");
        DECLARATIONS.forEach((name, type) ->
            sb.append("    static final ")
                .append(type.javaTypeName())
                .append(' ')
                .append(name)
                .append(" = ")
                .append(type.javaInitializer())
                .append(";\n"));
        sb.append("\n");
        sb.append("    public static void main(String[] args) throws InterruptedException {\n");
        sb.append(indent(userCode, "        "));
        sb.append("\n");
        sb.append("        // The simulator waits for chefs automatically.\n");
        sb.append("        // In real code you would call .join() on each Thread here.\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public Outcome run(String code) {
        try {
            Program program = Parser.parse(code, DECLARATIONS);
            SimulationResult sim = new Simulator().run(program, new LinkedHashMap<>());

            if (sim.hasError()) {
                return new Outcome(
                    false,
                    "Simulation halted: " + sim.error(),
                    sim,
                    List.of()
                );
            }

            int ovenAcquires = countAcquires(sim.events(), "oven");
            int fryerAcquires = countAcquires(sim.events(), "fryer");

            if (ovenAcquires < 2 || fryerAcquires < 2) {
                return new Outcome(
                    false,
                    "Each chef must cook with BOTH the oven and the fryer (current run: oven acquired "
                        + ovenAcquires + " time(s), fryer " + fryerAcquires + " time(s)).",
                    sim,
                    List.of()
                );
            }

            return new Outcome(
                true,
                "Both chefs plated their dishes — no deadlock.",
                sim,
                List.of()
            );

        } catch (ParseException e) {
            List<String> errors = new ArrayList<>();
            errors.add(e.getMessage());
            return new Outcome(false, "Parse error", null, errors);
        } catch (SimulationException e) {
            List<String> errors = new ArrayList<>();
            errors.add(e.getMessage());
            return new Outcome(false, "Runtime error", null, errors);
        }
    }

    private static int countAcquires(List<String> events, String lockName) {
        String needle = "acquires lock '" + lockName + "'";
        int count = 0;
        for (String event : events) {
            if (event.contains(needle)) {
                count++;
            }
        }
        return count;
    }

    private static String indent(String text, String prefix) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String line : text.split("\n", -1)) {
            if (!first) {
                sb.append('\n');
            }
            first = false;
            if (!line.isEmpty()) {
                sb.append(prefix).append(line);
            }
        }
        return sb.toString();
    }
}
