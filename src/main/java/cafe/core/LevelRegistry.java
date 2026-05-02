package cafe.core;

import java.util.List;

public final class LevelRegistry {

    private final List<Level> levels;

    public LevelRegistry(List<Level> levels) {
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("LevelRegistry requires at least one level");
        }
        this.levels = List.copyOf(levels);
    }

    public static LevelRegistry defaultRegistry() {
        return new LevelRegistry(List.of(
            new LostUpdateLevel(),
            new AtomicCounterLevel(),
            new DeadlockKitchenLevel()
        ));
    }

    public List<Level> levels() {
        return levels;
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
