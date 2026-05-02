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

        SimulationResult result = new Simulator(program, initial("counter", 41)).runToCompletion();

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

        SimulationResult result = new Simulator(program, initial("counter", 41)).runToCompletion();

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

        SimulationResult result = new Simulator(program, initial("counter", 41)).runToCompletion();

        assertNull(result.error());
        assertEquals(43, result.finalGlobals().get("counter"));
    }

    @Test
    void postIncrementStillRacesAndLosesAnUpdate() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> { counter++; });
            Thread.ofVirtual().start(() -> { counter++; });
            """, INT_LEVEL);

        SimulationResult result = new Simulator(program, initial("counter", 41)).runToCompletion();

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

        SimulationResult result = new Simulator(program, Map.of()).runToCompletion();

        assertNotNull(result.error());
        assertTrue(result.error().startsWith("Deadlock"));
    }

    @Test
    void recordsEventsForEachInstruction() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> { counter.incrementAndGet(); });
            """, ATOMIC_LEVEL);

        SimulationResult result = new Simulator(program, initial("counter", 0)).runToCompletion();

        assertFalse(result.events().isEmpty());
    }

    @Test
    void addAndGetAddsAtomically() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> { counter.addAndGet(3); });
            Thread.ofVirtual().start(() -> { counter.addAndGet(4); });
            """, ATOMIC_LEVEL);

        SimulationResult result = new Simulator(program, initial("counter", 10)).runToCompletion();

        assertNull(result.error());
        assertEquals(17, result.finalGlobals().get("counter"));
    }

    @Test
    void compareAndSetSucceedsOnlyWhenCurrentMatches() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> { counter.compareAndSet(0, 7); });
            """, ATOMIC_LEVEL);

        SimulationResult result = new Simulator(program, initial("counter", 0)).runToCompletion();
        assertEquals(7, result.finalGlobals().get("counter"));
    }

    @Test
    void compareAndSetFailsSilentlyWhenCurrentDoesNotMatch() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> { counter.compareAndSet(0, 7); });
            """, ATOMIC_LEVEL);

        SimulationResult result = new Simulator(program, initial("counter", 5)).runToCompletion();
        assertEquals(5, result.finalGlobals().get("counter"));
    }

    @Test
    void stepInstructionAdvancesOneChefAtATime() {
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

        Simulator sim = new Simulator(program, initial("counter", 41));
        assertTrue(sim.stepInstruction());
        assertEquals(1, sim.snapshot().events().size());
        assertTrue(sim.stepInstruction());
        assertEquals(2, sim.snapshot().events().size());
        // Run remaining instructions
        while (!sim.isFinished()) {
            sim.stepInstruction();
        }
        assertEquals(42, sim.snapshot().finalGlobals().get("counter"));
    }

    @Test
    void stepChefAdvancesOnlyTheSpecifiedChef() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> { counter.incrementAndGet(); });
            Thread.ofVirtual().start(() -> { counter.incrementAndGet(); });
            """, ATOMIC_LEVEL);

        Simulator sim = new Simulator(program, initial("counter", 0));

        assertTrue(sim.stepChef(0));
        assertEquals(1, sim.snapshot().finalGlobals().get("counter"));
        assertEquals(1, sim.snapshot().events().size());

        assertTrue(sim.stepChef(1));
        assertEquals(2, sim.snapshot().finalGlobals().get("counter"));
    }

    @Test
    void chefSnapshotsReportBlockedState() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> {
                synchronized (oven) { synchronized (fryer) {} }
            });
            Thread.ofVirtual().start(() -> {
                synchronized (oven) {}
            });
            """, KITCHEN);

        Simulator sim = new Simulator(program, Map.of());
        var initial = sim.chefSnapshots();
        assertEquals(2, initial.size());
        assertNull(initial.get(0).blockedOnLock());
        assertNull(initial.get(1).blockedOnLock());

        sim.stepChef(0);
        var snaps = sim.chefSnapshots();
        assertNull(snaps.get(0).blockedOnLock(), "chef 0 holds oven, next is lock fryer (free)");
        assertEquals("oven", snaps.get(1).blockedOnLock(), "chef 1 is waiting on oven");
    }

    @Test
    void stepInstructionDetectsDeadlockWhenNoChefCanProgress() {
        Program program = Parser.parse("""
            Thread.ofVirtual().start(() -> {
                synchronized (oven) {
                    synchronized (fryer) {}
                }
            });
            Thread.ofVirtual().start(() -> {
                synchronized (fryer) {
                    synchronized (oven) {}
                }
            });
            """, KITCHEN);

        Simulator sim = new Simulator(program, Map.of());
        while (!sim.isFinished()) {
            sim.stepInstruction();
        }
        assertNotNull(sim.snapshot().error());
        assertTrue(sim.snapshot().error().startsWith("Deadlock"));
    }

    @Test
    void stepRoundAdvancesOneRoundAtATime() {
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

        Simulator sim = new Simulator(program, initial("counter", 41));
        assertFalse(sim.isFinished());

        assertTrue(sim.stepRound());
        assertEquals(2, sim.snapshot().events().size());

        assertTrue(sim.stepRound());
        assertEquals(4, sim.snapshot().events().size());

        assertFalse(sim.stepRound());
        assertTrue(sim.isFinished());
        assertEquals(42, sim.snapshot().finalGlobals().get("counter"));
    }

    private static Map<String, Integer> initial(String name, int value) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put(name, value);
        return map;
    }
}
