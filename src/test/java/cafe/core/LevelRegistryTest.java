package cafe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LevelRegistryTest {

    @Test
    void defaultRegistryListsLevelsInTeachingOrder() {
        LevelRegistry registry = LevelRegistry.defaultRegistry();

        assertEquals(3, registry.size());
        assertEquals("lost-update", registry.get(0).id());
        assertEquals("atomic-counter", registry.get(1).id());
        assertEquals("deadlock-kitchen", registry.get(2).id());
    }

    @Test
    void indexOfReturnsPositionOrMinusOne() {
        LevelRegistry registry = LevelRegistry.defaultRegistry();

        assertEquals(0, registry.indexOf("lost-update"));
        assertEquals(1, registry.indexOf("atomic-counter"));
        assertEquals(2, registry.indexOf("deadlock-kitchen"));
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
