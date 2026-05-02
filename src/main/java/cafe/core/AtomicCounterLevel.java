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

public final class AtomicCounterLevel implements Level {

    private static final int EXPECTED = 43;
    private static final String COUNTER = "counter";

    private static final Map<String, SharedType> DECLARATIONS = Map.of(
        "counter", new SharedType.AtomicIntegerType(41)
    );

    private static final String STARTER = """
        // counter is an AtomicInteger that starts at 41.
        // Each chef must safely increment it once. The target is 43.

        Thread chef1 = Thread.ofVirtual().start(() -> {
            // Add the call that atomically increments counter.
        });

        Thread chef2 = Thread.ofVirtual().start(() -> {
            // Same here.
        });
        """;

    private static final List<String> HINTS = List.of(
        "AtomicInteger.incrementAndGet() does the read+add+write as a single atomic step.",
        "Each chef should call counter.incrementAndGet(); once.",
        "Notice that int operations like '++' or '=' don't apply here — counter is an AtomicInteger, not an int."
    );

    @Override
    public String id() {
        return "atomic-counter";
    }

    @Override
    public String title() {
        return "Level 2: Atomic Counter";
    }

    @Override
    public String intro() {
        return """
            Same scenario as Level 1, but the kitchen now uses an AtomicInteger.
            With the right primitive, you don't need an external lock. Find it.
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
        sb.append("import java.util.concurrent.atomic.AtomicInteger;\n\n");
        sb.append("public class AtomicCounterLesson {\n");
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
        sb.append("\n");
        sb.append("        System.out.println(\"Final counter: \" + counter.get());\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public Outcome run(String code) {
        Map<String, Integer> initial = new LinkedHashMap<>();
        DECLARATIONS.forEach((name, type) -> {
            if (type instanceof SharedType.IntType i) {
                initial.put(name, i.initialValue());
            } else if (type instanceof SharedType.AtomicIntegerType a) {
                initial.put(name, a.initialValue());
            }
        });

        try {
            Program program = Parser.parse(code, DECLARATIONS);
            SimulationResult sim = new Simulator().run(program, initial);

            if (sim.hasError()) {
                return new Outcome(
                    false,
                    "Simulation halted: " + sim.error(),
                    sim,
                    List.of(sim.error())
                );
            }

            int actual = sim.finalGlobals().getOrDefault(COUNTER, 0);
            if (actual == EXPECTED) {
                return new Outcome(
                    true,
                    "Counter ended at " + actual + " — atomic ops, no lock needed.",
                    sim,
                    List.of()
                );
            }
            String summary = "Counter ended at " + actual + ", expected " + EXPECTED + ".";
            return new Outcome(false, summary, sim, List.of());

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
