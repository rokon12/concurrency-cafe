package cafe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LevelRegistryTest {

    @Test
    void defaultRegistryListsLevelsInTeachingOrder() {
        LevelRegistry registry = LevelRegistry.defaultRegistry();

        assertEquals(13, registry.size());
        assertEquals("lost-update", registry.get(0).id());
        assertEquals("monitor-counter", registry.get(1).id());
        assertEquals("many-chefs", registry.get(2).id());
        assertEquals("same-monitor", registry.get(3).id());
        assertEquals("atomic-counter", registry.get(4).id());
        assertEquals("atomic-read-modify-write", registry.get(5).id());
        assertEquals("atomic-add", registry.get(6).id());
        assertEquals("reentrant-lock", registry.get(7).id());
        assertEquals("deadlock-kitchen", registry.get(8).id());
        assertEquals("lock-ordering", registry.get(9).id());
        assertEquals("wait-notify", registry.get(10).id());
        assertEquals("producer-consumer", registry.get(11).id());
        assertEquals("virtual-blocking-sleep", registry.get(12).id());
    }

    @Test
    void indexOfReturnsPositionOrMinusOne() {
        LevelRegistry registry = LevelRegistry.defaultRegistry();

        assertEquals(0, registry.indexOf("lost-update"));
        assertEquals(1, registry.indexOf("monitor-counter"));
        assertEquals(12, registry.indexOf("virtual-blocking-sleep"));
        assertEquals(-1, registry.indexOf("nonexistent"));
    }

    @Test
    void getByIndexReturnsSameInstanceFromLevelsList() {
        LevelRegistry registry = LevelRegistry.defaultRegistry();
        assertSame(registry.levels().get(0), registry.get(0));
    }

    @Test
    void outOfRangeIndexThrows() {
        LevelRegistry registry = LevelRegistry.defaultRegistry();
        assertThrows(IndexOutOfBoundsException.class, () -> registry.get(99));
    }

    @Test
    void emptyRegistryRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LevelRegistry(java.util.List.of()));
    }
}
