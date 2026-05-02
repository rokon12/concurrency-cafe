package cafe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LevelRegistryTest {

    @Test
    void defaultRegistryListsLevelsInTeachingOrder() {
        LevelRegistry registry = LevelRegistry.defaultRegistry();

        assertEquals(8, registry.size());
        assertEquals("lost-update", registry.get(0).id());
        assertEquals("many-chefs", registry.get(1).id());
        assertEquals("atomic-counter", registry.get(2).id());
        assertEquals("atomic-add", registry.get(3).id());
        assertEquals("reentrant-lock", registry.get(4).id());
        assertEquals("deadlock-kitchen", registry.get(5).id());
        assertEquals("producer-consumer", registry.get(6).id());
        assertEquals("virtual-vs-platform", registry.get(7).id());
    }

    @Test
    void indexOfReturnsPositionOrMinusOne() {
        LevelRegistry registry = LevelRegistry.defaultRegistry();

        assertEquals(0, registry.indexOf("lost-update"));
        assertEquals(1, registry.indexOf("many-chefs"));
        assertEquals(2, registry.indexOf("atomic-counter"));
        assertEquals(3, registry.indexOf("atomic-add"));
        assertEquals(4, registry.indexOf("reentrant-lock"));
        assertEquals(5, registry.indexOf("deadlock-kitchen"));
        assertEquals(6, registry.indexOf("producer-consumer"));
        assertEquals(7, registry.indexOf("virtual-vs-platform"));
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
