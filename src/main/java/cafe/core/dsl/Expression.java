package cafe.core.dsl;

public sealed interface Expression {

    record Literal(int value) implements Expression {
    }

    record Var(String name) implements Expression {
    }

    record BinOp(String op, Expression left, Expression right) implements Expression {
    }

    record AtomicGet(String name) implements Expression {
    }
}
