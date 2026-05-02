package cafe.core.dsl;

import java.util.List;

public record Program(List<ChefProgram> chefs) {

    public Program {
        chefs = List.copyOf(chefs);
    }
}
