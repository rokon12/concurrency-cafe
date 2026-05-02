package cafe.core;

import cafe.core.dsl.ParseException;
import cafe.core.dsl.Parser;
import cafe.core.dsl.Program;
import cafe.core.sim.SimulationException;
import cafe.core.sim.SimulationResult;
import cafe.core.sim.Simulator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface Level {

    String id();

    String title();

    String intro();

    String starterCode();

    List<String> hints();

    Map<String, SharedType> sharedDeclarations();

    String fullSourceWith(String userCode);

    String lessonClassName();

    Outcome validate(SimulationResult sim);

    default Map<String, Integer> initialGlobals() {
        Map<String, Integer> initial = new LinkedHashMap<>();
        sharedDeclarations().forEach((name, type) -> {
            if (type instanceof SharedType.IntType i) {
                initial.put(name, i.initialValue());
            } else if (type instanceof SharedType.AtomicIntegerType a) {
                initial.put(name, a.initialValue());
            }
        });
        return initial;
    }

    default Simulator startSimulation(String code) {
        Program program = Parser.parse(code, sharedDeclarations());
        return new Simulator(program, initialGlobals());
    }

    default Outcome run(String code) {
        try {
            Simulator sim = startSimulation(code);
            sim.runToCompletion();
            return validate(sim.snapshot());
        } catch (ParseException e) {
            return new Outcome(false, "Parse error", null, List.of(e.getMessage()));
        } catch (SimulationException e) {
            return new Outcome(false, "Runtime error", null, List.of(e.getMessage()));
        }
    }
}
