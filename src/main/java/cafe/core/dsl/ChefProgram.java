package cafe.core.dsl;

import java.util.List;

public record ChefProgram(String name, List<Instruction> instructions) {

    public ChefProgram {
        instructions = List.copyOf(instructions);
    }
}
