package cafe.core;

import cafe.core.levels.AtomicCounterLevel;
import cafe.core.levels.DeadlockKitchenLevel;
import cafe.core.levels.LostUpdateLevel;

import java.util.List;

public record LevelRegistry(List<Level> levels) {

    public LevelRegistry(List<Level> levels) {
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("LevelRegistry requires at least one level");
        }
        this.levels = List.copyOf(levels);
    }

    /**
     * The shipped sequence of levels, in teaching order. Add new levels here.
     * Each entry is a class in {@code cafe.core.levels}; see
     * {@code src/main/java/cafe/core/levels/README.md} for the recipe.
     */
    public static LevelRegistry defaultRegistry() {
        return new LevelRegistry(List.of(
            new LostUpdateLevel(),
            new AtomicCounterLevel(),
            new DeadlockKitchenLevel()
        ));
    }

    public int size() {
        return levels.size();
    }

    public Level get(int index) {
        if (index < 0 || index >= levels.size()) {
            throw new IndexOutOfBoundsException("level index " + index + " out of range [0, " + levels.size() + ")");
        }
        return levels.get(index);
    }

    public int indexOf(String levelId) {
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).id().equals(levelId)) {
                return i;
            }
        }
        return -1;
    }
}
