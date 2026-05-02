package cafe.core.sim;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SimulationResult(
    List<String> events,
    Map<String, Integer> finalGlobals,
    String error
) {

    public SimulationResult {
        events = List.copyOf(events);
        finalGlobals = Map.copyOf(new LinkedHashMap<>(finalGlobals));
    }

    public boolean hasError() {
        return error != null;
    }
}
