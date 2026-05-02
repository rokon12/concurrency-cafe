package cafe.core.sim;

import cafe.core.SharedType;
import cafe.core.dsl.Parser;
import cafe.core.dsl.Program;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatorTest {

    private static final Map<String, SharedType> INT_LEVEL = Map.of(
        "counter", new SharedType.IntType(0),
        "counterLock", new SharedType.MonitorType()
    );

    private static final Map<String, SharedType> ATOMIC_LEVEL = Map.of(
        "counter", new SharedType.AtomicIntegerType(0)
    );

    private static final Map<String, SharedType> KITCHEN = Map.of(
        "oven", new SharedType.MonitorType(),
        "fryer", new SharedType.MonitorType()
    );

    @Test
    void unsafeReadModifyWriteLosesAnUpdate() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> {
                int x = counter;
                counter = x + 1;
            });
            Thread.ofVirtual().start(() -> {
                int x = counter;
                counter = x + 1;
            });
            """, INT_LEVEL);

        SimulationResult result = new Simulator().run(program, initial("counter", 41));

        assertNull(result.error());
        assertEquals(42, result.finalGlobals().get("counter"));
    }

    @Test
    void atomicIncEliminatesLostUpdate() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> {
                counter.incrementAndGet();
            });
            Thread.ofVirtual().start(() -> {
                counter.incrementAndGet();
            });
            """, ATOMIC_LEVEL);

        SimulationResult result = new Simulator().run(program, initial("counter", 41));

        assertNull(result.error());
        assertEquals(43, result.finalGlobals().get("counter"));
    }

    @Test
    void synchronizedBlockSerializesAccess() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> {
                synchronized (counterLock) {
                    int x = counter;
                    counter = x + 1;
                }
            });
            Thread.ofVirtual().start(() -> {
                synchronized (counterLock) {
                    int x = counter;
                    counter = x + 1;
                }
            });
            """, INT_LEVEL);

        SimulationResult result = new Simulator().run(program, initial("counter", 41));

        assertNull(result.error());
        assertEquals(43, result.finalGlobals().get("counter"));
    }

    @Test
    void postIncrementStillRacesAndLosesAnUpdate() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> { counter++; });
            Thread.ofVirtual().start(() -> { counter++; });
            """, INT_LEVEL);

        SimulationResult result = new Simulator().run(program, initial("counter", 41));

        assertNull(result.error());
        assertEquals(42, result.finalGlobals().get("counter"));
    }

    @Test
    void detectsClassicTwoLockDeadlock() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> {
                synchronized (oven) {
                    synchronized (fryer) {
                    }
                }
            });
            Thread.ofVirtual().start(() -> {
                synchronized (fryer) {
                    synchronized (oven) {
                    }
                }
            });
            """, KITCHEN);

        SimulationResult result = new Simulator().run(program, Map.of());

        assertNotNull(result.error());
        assertTrue(result.error().startsWith("Deadlock"));
    }

    @Test
    void recordsEventsForEachInstruction() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> { counter.incrementAndGet(); });
            """, ATOMIC_LEVEL);

        SimulationResult result = new Simulator().run(program, initial("counter", 0));

        assertFalse(result.events().isEmpty());
    }

    private static Map<String, Integer> initial(String name, int value) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put(name, value);
        return map;
    }
}
