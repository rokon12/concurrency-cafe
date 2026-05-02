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
- Indentation-based DSL: `chef "Name":` blocks, `read` / `write` / `atomicInc` / `lock` / `unlock` / `synchronized(...)` / `log`
- Tokenizer + line-aware parser with helpful errors
- Round-robin deterministic simulator with deadlock detection
- `Level` interface + `Outcome` record
- Editor UI: `<textarea>` + Run / Reset, hint reveal after N consecutive failures

### v0.3 — Java syntax
- DSL replaced with a real subset of Java 21+
- `Thread.ofVirtual().start(() -> { ... });` and `Thread.ofPlatform()`
- Statements: `int x = expr;`, `x = expr;`, `x++` / `+=`, `x.method(args)`, `synchronized (m) { ... }`, `System.out.println("...")`
- Recursive-descent parser; expressions lift global reads into temp `Read` + `LocalSet`
- `Show full Java source` button reveals the wrapping class with declarations spliced in
- Compiles AS-IS into a real `.java` file on Java 21+

### v0.4 — Strict type system
- `cafe.core.SharedType` sealed: `IntType`, `AtomicIntegerType`, `MonitorType`, `LockType`
- `Level.sharedDeclarations()` returns `Map<String, SharedType>`
- Parser type-checks every operation; mismatched ops fail with messages that name the actual type and suggest the right syntax
- L1 declares `int counter = 41;` + `Object counterLock = new Object();` — solutions are `synchronized` blocks (atomic moves to L2)
- 26 JUnit 5 tests covering tokenizer, parser, simulator, and level

## Pending

| # | Task |
|---|---|
| 17 | Add Level 2: AtomicInteger Counter |
| 18 | Add Level 3: Deadlock Kitchen |
| 19 | Build LevelRegistry + level navigation UI |
| 20 | Persist progress via localStorage |
| 21 | Add step-through mode (manual one-tick-at-a-time) |

**Suggested order:** 17 → 19 → 18 → 20 → 21.
L2 first (small, exercises typed parser end-to-end). Then navigation so multiple
levels feel like a game. L3 once navigation exists. localStorage and stepping
are polish that benefit from having ≥3 levels in place.

## Backlog (parked, not tracked as tasks)

- CodeMirror-based syntax highlighting in the editor
- Animation / replay of the simulation trace at a configurable speed
- User-shaped schedules (pick which chef runs next instead of round-robin)
- More `AtomicInteger` ops: `compareAndSet`, `addAndGet`, `getAndAdd`
- `.get()` as an expression so AtomicInteger can be read inline
- Producer / Consumer level (`BlockingQueue`, `wait` / `notify`)
- Virtual-thread vs platform-thread blocking comparison level
- Generated downloadable `.java` file from the editor contents
