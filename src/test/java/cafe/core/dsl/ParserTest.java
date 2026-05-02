package cafe.core.dsl;

import cafe.core.SharedType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserTest {

    private static final Map<String, SharedType> INT_LEVEL = Map.of(
        "counter", new SharedType.IntType(41),
        "counterLock", new SharedType.MonitorType()
    );

    private static final Map<String, SharedType> ATOMIC_LEVEL = Map.of(
        "counter", new SharedType.AtomicIntegerType(41)
    );

    private static final Map<String, SharedType> LOCK_LEVEL = Map.of(
        "counter", new SharedType.IntType(0),
        "counterLock", new SharedType.LockType()
    );

    @Test
    void parsesTwoVirtualThreadChefs() {
        Program program = Parser.parse("""
            Thread chef1 = Thread.ofVirtual().start(() -> {
                int x = counter;
                counter = x + 1;
            });

            Thread chef2 = Thread.ofVirtual().start(() -> {
                int x = counter;
                counter = x + 1;
            });
            """, INT_LEVEL);

        assertEquals(2, program.chefs().size());
        assertEquals("chef1", program.chefs().get(0).name());
        assertEquals("chef2", program.chefs().get(1).name());

        List<Instruction> body = program.chefs().get(0).instructions();
        assertEquals(2, body.size());
        assertInstanceOf(Instruction.Read.class, body.get(0));
        assertInstanceOf(Instruction.Write.class, body.get(1));
    }

    @Test
    void unnamedChefsGetAutoNamesByThreadKind() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> {
                counter++;
            });
            Thread.ofPlatform().start(() -> {
                counter++;
            });
            """, INT_LEVEL);

        assertEquals("vthread-1", program.chefs().get(0).name());
        assertEquals("thread-1", program.chefs().get(1).name());
    }

    @Test
    void atomicCallCompilesToAtomicIncOnAtomicIntegerType() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> {
                counter.incrementAndGet();
            });
            """, ATOMIC_LEVEL);

        var body = program.chefs().get(0).instructions();
        assertEquals(1, body.size());
        Instruction.AtomicInc inc = assertInstanceOf(Instruction.AtomicInc.class, body.get(0));
        assertEquals("counter", inc.globalName());
    }

    @Test
    void postIncrementCompilesToReadPlusWriteOnIntType() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> {
                counter++;
            });
            """, INT_LEVEL);

        var body = program.chefs().get(0).instructions();
        assertEquals(2, body.size());
        assertInstanceOf(Instruction.Read.class, body.get(0));
        assertInstanceOf(Instruction.Write.class, body.get(1));
    }

    @Test
    void synchronizedExpandsToLockUnlockOnMonitorType() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> {
                synchronized (counterLock) {
                    int x = counter;
                    counter = x + 1;
                }
            });
            """, INT_LEVEL);

        var body = program.chefs().get(0).instructions();
        assertEquals(4, body.size());
        assertInstanceOf(Instruction.Lock.class, body.get(0));
        assertInstanceOf(Instruction.Read.class, body.get(1));
        assertInstanceOf(Instruction.Write.class, body.get(2));
        assertInstanceOf(Instruction.Unlock.class, body.get(3));
    }

    @Test
    void manualLockCompilesToLockUnlockOnLockType() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> {
                counterLock.lock();
                counter++;
                counterLock.unlock();
            });
            """, LOCK_LEVEL);

        var body = program.chefs().get(0).instructions();
        assertInstanceOf(Instruction.Lock.class, body.get(0));
        assertInstanceOf(Instruction.Unlock.class, body.get(body.size() - 1));
    }

    @Test
    void rejectsIncrementOnAtomicInteger() {
        ParseException e = assertThrows(ParseException.class, () -> Parser.parse("""
            Thread.ofVirtual().start(() -> {
                counter++;
            });
            """, ATOMIC_LEVEL));
        assertTrue(e.getMessage().contains("AtomicInteger"));
        assertTrue(e.getMessage().contains("incrementAndGet"));
    }

    @Test
    void rejectsAtomicCallOnInt() {
        ParseException e = assertThrows(ParseException.class, () -> Parser.parse("""
            Thread.ofVirtual().start(() -> {
                counter.incrementAndGet();
            });
            """, INT_LEVEL));
        assertTrue(e.getMessage().contains("int"));
    }

    @Test
    void rejectsSynchronizedOnLockType() {
        ParseException e = assertThrows(ParseException.class, () -> Parser.parse("""
            Thread.ofVirtual().start(() -> {
                synchronized (counterLock) {
                    counter++;
                }
            });
            """, LOCK_LEVEL));
        assertTrue(e.getMessage().contains("ReentrantLock"));
    }

    @Test
    void rejectsLockMethodOnMonitorType() {
        ParseException e = assertThrows(ParseException.class, () -> Parser.parse("""
            Thread.ofVirtual().start(() -> {
                counterLock.lock();
            });
            """, INT_LEVEL));
        assertTrue(e.getMessage().contains("Object monitor")
            || e.getMessage().contains("ReentrantLock"));
    }

    @Test
    void rejectsReadingAtomicIntegerAsInt() {
        assertThrows(ParseException.class, () -> Parser.parse("""
            Thread.ofVirtual().start(() -> {
                int x = counter;
            });
            """, ATOMIC_LEVEL));
    }

    @Test
    void readingGlobalInsideExpressionLiftsToTempPlusLocalSet() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> {
                int y = counter + 1;
            });
            """, INT_LEVEL);

        var body = program.chefs().get(0).instructions();
        assertEquals(2, body.size());
        assertInstanceOf(Instruction.Read.class, body.get(0));
        assertInstanceOf(Instruction.LocalSet.class, body.get(1));
    }

    @Test
    void rejectsControlFlow() {
        assertThrows(ParseException.class, () -> Parser.parse("""
            Thread.ofVirtual().start(() -> {
                if (x > 0) { counter++; }
            });
            """, INT_LEVEL));
    }

    @Test
    void rejectsUndeclaredVariable() {
        assertThrows(ParseException.class, () -> Parser.parse("""
            Thread.ofVirtual().start(() -> {
                unknown = 1;
            });
            """, INT_LEVEL));
    }

    @Test
    void atomicSetCompilesToWrite() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> {
                counter.set(7);
            });
            """, ATOMIC_LEVEL);

        var body = program.chefs().get(0).instructions();
        assertInstanceOf(Instruction.Write.class, body.get(0));
    }
}
