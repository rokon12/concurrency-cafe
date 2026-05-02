# How to add a level

Each level lives as a single class in this package. The shape:

```java
package cafe.core.levels;

import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.sim.SimulationResult;

public final class MyLevel extends AbstractLevel {

    public MyLevel() {
        super(LevelSpec.builder()
            .id("my-level")                              // URL-safe; used as key in localStorage and on disk
            .title("Level N: Your Title")                // shown in breadcrumb + lesson header
            .chapter("Chapter X · Theme")                // shown above the title
            .lessonClassName("MyLevelLesson")            // the wrapping Java class name in `Show full source` / Download
            .passingCondition("Counter ends at 43")      // short label for the objective card
            .declare("counter", new SharedType.IntType(0))
            .declare("monitor", new SharedType.MonitorType())
            .intro("""
                One or two sentences setting up the scenario.
                Shown under the level title.
                """)
            .starterCode("""
                Thread chef1 = Thread.ofVirtual().start(() -> {
                    // ... starter code the user begins editing ...
                });
                """)
            .hint("Shown after 2 fails.")
            .hint("Shown after 4 fails.")
            .hint("Final hint, near-spoiler.")
            .resultPrintln("\"Final counter: \" + counter")  // optional; null skips the println
            .build());
    }

    @Override
    public Outcome validate(SimulationResult sim) {
        if (sim.hasError()) {
            return haltedOnError(sim);                    // helper from AbstractLevel
        }
        // ... level-specific pass/fail logic ...
        // Use pass(...) / fail(...) helpers to construct the Outcome.
    }
}
```

## Wiring

Open `cafe.core.LevelRegistry.defaultRegistry()` and add your class to the
list. The order determines progression order in the breadcrumb.

```java
public static LevelRegistry defaultRegistry() {
    return new LevelRegistry(List.of(
        new LostUpdateLevel(),
        new AtomicCounterLevel(),
        new DeadlockKitchenLevel(),
        new MyLevel()                  // ← add here
    ));
}
```

## Type system

The DSL parser is type-aware. Each declared shared name has a concrete type
that constrains which operations are valid:

| Type                            | Allowed in user code                                        |
| ------------------------------- | ----------------------------------------------------------- |
| `SharedType.IntType(initial)`   | `int x = v;`, `v = expr;`, `v++`, `v += n`                  |
| `SharedType.AtomicIntegerType`  | `v.incrementAndGet()`, `v.addAndGet(n)`, `v.compareAndSet(o,n)`, `v.set(x)`, `v.get()` |
| `SharedType.MonitorType`        | `synchronized (m) { ... }`                                  |
| `SharedType.LockType`           | `l.lock();`, `l.unlock();`                                  |

Mismatched ops fail at parse time with a message that names the actual
type and suggests the right syntax. You don't have to defend against
"user wrote `counter++` on AtomicInteger" — the parser already does.

## Tests

Add a test in `src/test/java/cafe/core/levels/MyLevelTest.java`:

```java
package cafe.core.levels;

import cafe.core.Outcome;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MyLevelTest {
    private final MyLevel level = new MyLevel();

    @Test
    void starterFails() {
        assertFalse(level.run(level.starterCode()).passed());
    }

    @Test
    void canonicalSolutionPasses() {
        Outcome outcome = level.run("""
            Thread.ofVirtual().start(() -> { /* the right fix */ });
            Thread.ofVirtual().start(() -> { /* the right fix */ });
            """);
        assertTrue(outcome.passed(), outcome.summary());
    }
}
```

## What `AbstractLevel` gives you

- All `Level` interface getters wired through to your `LevelSpec`
- `fullSourceWith(userCode)` generates a runnable Java class:
  - Imports inferred from `SharedType`s (e.g., `AtomicInteger`, `ReentrantLock`)
  - `static` for `int` (mutable), `static final` for everything else
  - Optional `System.out.println(...)` from `resultPrintln`
- `pass(summary, sim)` / `fail(summary, sim)` / `haltedOnError(sim)` helpers
- `Level.run(code)` (default on `Level`) wires your `validate` into the parse + simulate pipeline
