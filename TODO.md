# Concurrency Café — Roadmap

A TeaVM-compiled, Java-as-DSL teaching game for concurrency hazards.
Editor accepts a strict subset of Java 21+ syntax; round-robin deterministic
simulator surfaces races and deadlocks; per-level type system enforces
real Java semantics.

## Done

### v0.1 — TeaVM bootstrap
- TeaVM 0.13.1 Gradle plugin wired (JS target, `cafe.js`)
- `src/main/webapp/index.html` + `style.css` shell
- `cafe.web.Browser` JS interop bridge (`@JSBody`, `@JSFunctor`)
- `cafe.web.Main` entry point
- `./gradlew build` produces a runnable WAR

### v0.2 — DSL + simulator
- Indentation-based DSL with `read` / `write` / `atomicInc` / `lock` / `unlock` / `synchronized` / `log`
- Tokenizer + line-aware parser with helpful errors
- Round-robin deterministic simulator with deadlock detection
- `Level` interface + `Outcome` record
- Editor UI: `<textarea>` + Run / Reset, hint reveal after N consecutive failures

### v0.3 — Java syntax
- DSL replaced with a real subset of Java 21+ (`Thread.ofVirtual().start(() -> { ... })`)
- Statements: `int x = expr;`, `x = expr;`, `x++` / `+=`, `x.method(args)`, `synchronized (m) { ... }`, `System.out.println("...")`
- Recursive-descent parser; expressions lift global reads into temp `Read` + `LocalSet`
- `Show full Java source` button reveals the wrapping class with declarations spliced in
- Compiles AS-IS into a real `.java` file on Java 21+

### v0.4 — Strict type system
- `cafe.core.SharedType` sealed: `IntType`, `AtomicIntegerType`, `MonitorType`, `LockType`
- `Level.sharedDeclarations()` returns `Map<String, SharedType>`
- Parser type-checks every operation; mismatched ops fail with messages that name the actual type and suggest the right syntax
- L1 declares `int counter = 41;` + `Object counterLock = new Object();` — solutions are `synchronized` blocks

### v0.5 — Multi-level + persistence + step-through
- L2: AtomicInteger Counter — `counter.incrementAndGet()` / `.set(...)`
- L3: Deadlock Kitchen — two `Object` monitors, fixed via consistent lock ordering
- `LevelRegistry` orders levels; UI breadcrumb tabs show progress and current location
- Previous / Next nav buttons; tabs locked until prior levels are completed
- localStorage persistence: completed-level set, last-edited code per level, current level — survives reloads
- Reset button clears the current level's saved code only
- **Step** button: round-robin one tick at a time. Refactored `Simulator` (stateful constructor + `stepRound()` / `runToCompletion()`) and `Level` (extracted `validate()` + `startSimulation()` defaults)

## Pending

(none — let me know what's next)

## Backlog (parked, not tracked as tasks)

- CodeMirror-based syntax highlighting in the editor
- Animation / replay of the simulation trace at a configurable speed
- User-shaped schedules (pick which chef runs next instead of round-robin)
- More `AtomicInteger` ops: `compareAndSet`, `addAndGet`, `getAndAdd`
- `.get()` as an expression so AtomicInteger can be read inline
- Producer / Consumer level (`BlockingQueue`, `wait` / `notify`)
- Virtual-thread vs platform-thread blocking comparison level
- Generated downloadable `.java` file from the editor contents
- Step one chef at a time (currently steps a full round)
- Reset progress button (clear all completed + saved code)
