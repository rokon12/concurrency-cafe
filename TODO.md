# Concurrency Café — Roadmap

A TeaVM-compiled, Java-as-DSL teaching game for concurrency hazards.
Editor accepts a strict subset of Java 21+ syntax with CodeMirror highlighting;
round-robin deterministic simulator surfaces races and deadlocks; per-level
type system enforces real Java semantics.

## Done

### v0.1 — TeaVM bootstrap
TeaVM 0.13.1 plugin, JS target, HTML/CSS shell, Browser JS interop bridge,
Main entry point, runnable WAR.

### v0.2 — DSL + simulator
Indentation-based DSL with read/write/atomicInc/lock/unlock/synchronized/log,
tokenizer + parser, round-robin simulator with deadlock detection, Level
interface, editor textarea + Run/Reset, hint reveal after N consecutive fails.

### v0.3 — Java syntax
DSL replaced with a real subset of Java 21+ (`Thread.ofVirtual().start(() -> {...})`).
Statements: `int x = expr;`, `x = expr;`, `x++` / `+=`, `x.method(args)`,
`synchronized (m) { ... }`, `System.out.println("...")`. Recursive-descent parser
lifts global reads into temp `Read` + `LocalSet`. Show-full-Java-source button.

### v0.4 — Strict type system
`SharedType` sealed (IntType, AtomicIntegerType, MonitorType, LockType).
`Level.sharedDeclarations()` returns `Map<String, SharedType>`. Parser type-checks
every operation; mismatched ops fail with messages that name the actual type and
suggest the right syntax.

### v0.5 — Multi-level + persistence + step-through
- L2: AtomicInteger Counter (`counter.incrementAndGet()` / `.set(...)`)
- L3: Deadlock Kitchen (two `Object` monitors, fixed via consistent lock ordering)
- `LevelRegistry` orders levels; UI breadcrumb tabs lock until prior levels pass
- Previous / Next nav buttons
- localStorage persistence: completed-level set, last-edited code per level, current level
- Step button advances the simulation one round at a time
- `Simulator` refactored to be stateful (constructor + `stepRound()` / `runToCompletion()`)
- `Level` gains `validate()` + default `run()` orchestration

### v0.6 — Polish + scheduling controls + atomic ops + editor upgrade
- Clear-all-progress button (with confirm)
- Step-one-chef-at-a-time (`Simulator.stepInstruction()`); UI Step now advances exactly one chef
- User-shaped schedules: per-chef "Step this chef" buttons during stepping; status shows blocked-on-lock
- Animation: Play / Pause button auto-advances at 500 ms intervals
- More AtomicInteger ops: `addAndGet`, `getAndAdd`, `compareAndSet` (new `AtomicAdd` and `AtomicCAS` instructions)
- `.get()` as an expression: `int x = counter.get() + 1;` works on AtomicInteger; rejected on int
- Downloadable `.java` file (Blob + URL.createObjectURL), named after `Level.lessonClassName()`
- CodeMirror 6 with Java highlighting (loaded via jsdelivr ES module CDN); replaces textarea

54 JUnit 5 tests pass.

## Pending (deferred — both require non-trivial new sim primitives)

### Producer / Consumer level
Adds a teaching scenario for blocking queues. Needs:
- New `SharedType.QueueType(capacity)` modeling `ArrayBlockingQueue<Integer>`
- New instructions `QueuePut`, `QueueTake` that block when full / empty (return `false` from `step`)
- Simulator gains `Map<String, Deque<Integer>> queues` alongside the int globals
- Parser cases for `queue.put(value)` and `int x = queue.take()`
- New `Expression.QueueTake` for use inside expressions (parallel to AtomicGet)
- A `Simulator(Program, Map<String,Integer>, Map<String,SharedType>)` constructor (so the sim knows which globals are queues and their capacities)
- Level: producers + consumers; validator ensures all items flow through without loss

### Virtual-vs-Platform thread comparison level
Models thread-pool exhaustion. Needs:
- Per-chef thread kind threaded from parser → `ChefProgram` → `Simulator`
- New `Instruction.Sleep(durationTicks)` simulating a blocking I/O wait
- Simulator gains a "platform thread pool size" — only N platform-thread chefs may be runnable at a time; sleeping holds the slot
- Virtual-thread chefs are not pool-bounded (they "park")
- Two-mode level: same code, different `Thread.ofPlatform()` vs `Thread.ofVirtual()` declarations show the starvation difference

Both items are sized like a v0.5-style milestone of their own — design + implementation + tests in a focused pass. They're queued and ready when you want them.

## Backlog (parked, not tracked as tasks)

- Self-host CodeMirror so the editor works offline (currently uses jsdelivr CDN)
- Configurable Play speed (slider)
- More int ops: `*`, `/`, `%` already lex/parse but not all combos exercised
- `if`/`while` control flow (would require richer simulator + parser)
- Multi-line `System.out.println` with string concat
- Per-level "highlight current line" while stepping (CodeMirror decoration)
