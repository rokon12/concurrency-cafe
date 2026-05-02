package cafe.core;

import cafe.core.sim.SimulationResult;

import java.util.List;

public record Outcome(
    boolean passed,
    String summary,
    SimulationResult simulation,
    List<String> errors
) {

    public Outcome {
        errors = List.copyOf(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
