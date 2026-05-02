package cafe.core.dsl;

import java.util.List;

/**
 * One chef in a level. {@code executorName} is non-null when the chef was
 * spawned via {@code pool.submit(() -> { ... })} — the simulator constrains
 * concurrent execution to that executor's pool size.
 */
public record ChefProgram(String name, ThreadKind kind, String executorName,
                          List<Instruction> instructions) {

    public ChefProgram {
        instructions = List.copyOf(instructions);
    }

    public ChefProgram(String name, ThreadKind kind, List<Instruction> instructions) {
        this(name, kind, null, instructions);
    }

    public ChefProgram(String name, List<Instruction> instructions) {
        this(name, ThreadKind.VIRTUAL, null, instructions);
    }
}
