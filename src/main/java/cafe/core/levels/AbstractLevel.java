package cafe.core.levels;

import cafe.core.Level;
import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Base class for levels. Subclasses pass a {@link LevelSpec} to the
 * constructor and override {@link #validate(SimulationResult)}.
 *
 * <p>Boilerplate handled here: all metadata getters, the
 * {@code fullSourceWith} class-template generator (with imports inferred
 * from the declared {@link SharedType}s), and the standard
 * {@code static} / {@code static final} modifier choice (mutable for
 * {@code int}, final for everything else).
 */
public abstract class AbstractLevel implements Level {

    protected final LevelSpec spec;

    protected AbstractLevel(LevelSpec spec) {
        this.spec = spec;
    }

    @Override public String id() { return spec.id(); }
    @Override public String title() { return spec.title(); }
    @Override public String intro() { return spec.intro(); }
    @Override public String starterCode() { return spec.starterCode(); }
    @Override public List<String> hints() { return spec.hints(); }
    @Override public Map<String, SharedType> sharedDeclarations() { return spec.declarations(); }
    @Override public String lessonClassName() { return spec.lessonClassName(); }
    @Override public String chapter() { return spec.chapter(); }
    @Override public String passingCondition() { return spec.passingCondition(); }

    @Override
    public String fullSourceWith(String userCode) {
        StringBuilder sb = new StringBuilder();

        Set<String> imports = new TreeSet<>();
        for (SharedType t : spec.declarations().values()) {
            if (t instanceof SharedType.AtomicIntegerType) {
                imports.add("java.util.concurrent.atomic.AtomicInteger");
            } else if (t instanceof SharedType.LockType) {
                imports.add("java.util.concurrent.locks.ReentrantLock");
            } else if (t instanceof SharedType.QueueType) {
                imports.add("java.util.concurrent.ArrayBlockingQueue");
            }
        }
        for (String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
        }
        if (!imports.isEmpty()) sb.append('\n');

        sb.append("public class ").append(spec.lessonClassName()).append(" {\n");

        for (var entry : spec.declarations().entrySet()) {
            String name = entry.getKey();
            SharedType t = entry.getValue();
            boolean mutable = t instanceof SharedType.IntType;
            sb.append("    static ");
            if (!mutable) sb.append("final ");
            sb.append(t.javaTypeName()).append(' ').append(name)
                .append(" = ").append(t.javaInitializer()).append(";\n");
        }
        sb.append('\n');

        sb.append("    public static void main(String[] args) throws InterruptedException {\n");
        sb.append(indent(userCode, "        "));
        sb.append('\n');
        sb.append("        // The simulator waits for chefs automatically.\n");
        sb.append("        // In real code you would call .join() on each Thread here.\n");

        if (spec.resultPrintlnExpression() != null) {
            sb.append('\n');
            sb.append("        System.out.println(")
                .append(spec.resultPrintlnExpression()).append(");\n");
        }

        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** Helper for subclasses' {@code validate} methods. */
    protected static Outcome pass(String summary, SimulationResult sim) {
        return new Outcome(true, summary, sim, List.of());
    }

    /** Helper for subclasses' {@code validate} methods. */
    protected static Outcome fail(String summary, SimulationResult sim) {
        return new Outcome(false, summary, sim, List.of());
    }

    /** Helper for subclasses to short-circuit on simulation errors. */
    protected static Outcome haltedOnError(SimulationResult sim) {
        return new Outcome(false, "Simulation halted: " + sim.error(), sim, List.of(sim.error()));
    }

    private static String indent(String text, String prefix) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String line : text.split("\n", -1)) {
            if (!first) sb.append('\n');
            first = false;
            if (!line.isEmpty()) sb.append(prefix).append(line);
        }
        return sb.toString();
    }
}
