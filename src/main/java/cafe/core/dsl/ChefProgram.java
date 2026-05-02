package cafe.core.dsl;

import java.util.List;

public record ChefProgram(String name, ThreadKind kind, List<Instruction> instructions) {

    public ChefProgram {
        instructions = List.copyOf(instructions);
    }

    public ChefProgram(String name, List<Instruction> instructions) {
        this(name, ThreadKind.VIRTUAL, instructions);
    }
}
