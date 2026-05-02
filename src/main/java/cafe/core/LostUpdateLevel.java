package cafe.core;

import cafe.core.sim.SimulationResult;

import java.util.List;
import java.util.Map;

public final class LostUpdateLevel implements Level {

    private static final int EXPECTED = 43;
    private static final String COUNTER = "counter";

    private static final Map<String, SharedType> DECLARATIONS = Map.of(
        "counter", new SharedType.IntType(41),
        "counterLock", new SharedType.MonitorType()
    );

    private static final String STARTER = """
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
        """;

    private static final List<String> HINTS = List.of(
        "Both chefs read counter before either writes back. The second write overwrites the first.",
        "Wrap the read+write in `synchronized (counterLock) { ... }` so only one chef is inside at a time.",
        "Manual locking also works: declare a ReentrantLock-shaped variable and call `.lock()` / `.unlock()`. (For now, this level only ships counterLock as an Object monitor — synchronized is the path here.)"
    );

    @Override
    public String id() {
        return "lost-update";
    }

    @Override
    public String title() {
        return "Level 1: Lost Update";
    }

    @Override
    public String intro() {
        return """
            Two chefs increment counter. It starts at 41 and should end at 43.
            Run the starter code first to see the bug, then fix it so no update is lost.
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
    public String lessonClassName() {
        return "LostUpdateLesson";
    }

    @Override
    public String passingCondition() {
        return "Counter ends at 43";
    }

    @Override
    public String fullSourceWith(String userCode) {
        StringBuilder sb = new StringBuilder();
        sb.append("public class LostUpdateLesson {\n");
        DECLARATIONS.forEach((name, type) ->
            sb.append("    static ")
                .append(type.javaTypeName())
                .append(' ')
                .append(name)
                .append(" = ")
                .append(type.javaInitializer())
                .append(";\n"));
        sb.append("\n");
        sb.append("    public static void main(String[] args) throws InterruptedException {\n");
        sb.append(LevelText.indent(userCode, "        "));
        sb.append("\n");
        sb.append("        // The simulator waits for chefs automatically.\n");
        sb.append("        // In real code you would call .join() on each Thread here.\n");
        sb.append("\n");
        sb.append("        System.out.println(\"Final counter: \" + counter);\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public Outcome validate(SimulationResult sim) {
        if (sim.hasError()) {
            return new Outcome(false, "Simulation halted: " + sim.error(), sim, List.of(sim.error()));
        }
        int actual = sim.finalGlobals().getOrDefault(COUNTER, 0);
        if (actual == EXPECTED) {
            return new Outcome(true, "Counter ended at " + actual + ". Both increments stuck.", sim, List.of());
        }
        int lost = EXPECTED - actual;
        String summary = "Counter ended at " + actual + ", expected " + EXPECTED
            + " (" + lost + " update" + (lost == 1 ? "" : "s") + " lost).";
        return new Outcome(false, summary, sim, List.of());
    }
}
