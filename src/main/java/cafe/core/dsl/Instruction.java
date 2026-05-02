package cafe.core.dsl;

public sealed interface Instruction {

    int line();

    record Read(String localName, String globalName, int line) implements Instruction {
    }

    record Write(String globalName, Expression value, int line) implements Instruction {
    }

    record AtomicInc(String globalName, int line) implements Instruction {
    }

    record AtomicAdd(String globalName, Expression delta, int line) implements Instruction {
    }

    record AtomicCAS(String globalName, Expression expected, Expression newValue, int line) implements Instruction {
    }

    record Lock(String lockName, int line) implements Instruction {
    }

    record Unlock(String lockName, int line) implements Instruction {
    }

    record Log(String message, int line) implements Instruction {
    }

    record LocalSet(String localName, Expression value, int line) implements Instruction {
    }

    record QueuePut(String queueName, Expression value, int line) implements Instruction {
    }

    record QueueTake(String localName, String queueName, int line) implements Instruction {
    }
}
