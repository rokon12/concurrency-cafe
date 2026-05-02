# Concurrency Café — Roadmap

## Deploying to GitHub Pages

A workflow at `.github/workflows/deploy-pages.yml` builds the WAR, extracts
its static content (`index.html`, `style.css`, `js/cafe.js`), strips the
servlet plumbing (`META-INF/`, `WEB-INF/`), and publishes via the official
GitHub Pages action.

**One-time setup:**

1. Push this repo to GitHub (`git push -u origin main`).
2. In the repo: **Settings → Pages → Source = "GitHub Actions"**.
3. Push to `main` (or hit *Run workflow* manually) — the workflow runs and
   the URL appears under the deploy step (`https://<user>.github.io/<repo>/`).

The CodeMirror editor loads via the jsdelivr / esm.sh CDN, so the deployed
site needs internet at runtime (until we self-host the bundle — backlog item).



A TeaVM-compiled, Java-as-DSL teaching game for concurrency hazards.
Editor accepts a strict subset of Java 21+ syntax with CodeMirror highlighting;
round-robin deterministic simulator surfaces races, deadlocks, queue cooperation,
and platform-thread starvation; per-level type system enforces real Java semantics.

## Levels (8 shipped)

| # | Title | Chapter | Type system | Lesson |
|---|---|---|---|---|
| 1 | Lost Update | I · Shared state | int + Object monitor | `synchronized` blocks |
| 2 | Many Chefs, One Counter | I · Shared state | int + Object monitor | the race scales — same lock works |
| 3 | Atomic Counter | I · Shared state | AtomicInteger | `incrementAndGet()` |
| 4 | Atomic Add | I · Shared state | AtomicInteger | `addAndGet(n)` for non-1 deltas |
| 5 | Explicit Lock | II · Locks & coordination | int + ReentrantLock | `lock()` / `unlock()` |
| 6 | Deadlock Kitchen | II · Locks & coordination | two Object monitors | consistent lock ordering |
| 7 | Producer / Consumer | II · Locks & coordination | ArrayBlockingQueue\<Integer\> + int | `put` / `take` cooperation |
| 8 | Virtual vs Platform | III · Modern Java | ArrayBlockingQueue + int, pool=1 | `Thread.ofVirtual()` to escape platform-pool starvation |

## Done

### v0.1 — TeaVM bootstrap
TeaVM 0.13.1 plugin, JS target, HTML/CSS shell, Browser JS interop bridge,
Main entry point, runnable WAR.

### v0.2 — DSL + simulator
Indentation-based DSL, tokenizer + parser, round-robin simulator with deadlock
detection, Level interface, editor textarea + Run/Reset, hint reveal.

### v0.3 — Java syntax
Real subset of Java 21+ in the editor (`Thread.ofVirtual().start(() -> {...})`),
recursive-descent parser, `Show full Java source` button.

### v0.4 — Strict type system
`SharedType` sealed: `IntType`, `AtomicIntegerType`, `MonitorType`, `LockType`.
Parser type-checks every operation with helpful messages.

### v0.5 — Multi-level + persistence + step-through
Level 1–3, breadcrumb tabs, localStorage, Step / Run-to-end, stateful Simulator.

### v0.6 — Polish + scheduling controls + atomic ops + editor upgrade
Clear-all-progress, step-one-chef, user-shaped schedules (per-chef step buttons),
Play/Pause animation, addAndGet / getAndAdd / compareAndSet, `.get()` in
expressions, Download .java, CodeMirror 6 with oneDark theme.

### v0.7 — Visual redesign + level reorganization + curriculum expansion
- Adopted `design/` lesson page: dark theme + theme toggle, sticky topbar,
  lesson header with objective card, two-pane grid, kitchen visualization
  (chef cards + counter stage + lock indicator), aux rail (hint / reference /
  book CTA), success overlay
- Levels moved into `cafe.core.levels` with `LevelSpec` + `AbstractLevel`;
  README walks through "How to add a level"
- Atomic Add and Explicit Lock levels added (4, 5)
- Many Chefs scaling level (#2)
- Producer / Consumer level + queue primitives (#7):
  - `SharedType.QueueType(capacity)`, `Instruction.QueuePut/QueueTake`,
    `Expression.QueueTake`, simulator queues, parser put/take, fast-paths
- Virtual vs Platform level + thread-kind primitives (#8):
  - `cafe.core.dsl.ThreadKind` (VIRTUAL / PLATFORM), `ChefProgram.kind`,
    parser captures kind from `Thread.of{Virtual,Platform}()`, simulator
    gains `setPlatformPoolSize` so platform chefs share a bounded pool
    (slots logged on acquire/release)
- 75 JUnit 5 tests pass

## Backlog (parked, not tracked as tasks)

- Self-host CodeMirror so the editor works offline (currently uses jsdelivr CDN)
- Configurable Play speed (slider; currently fixed at 500 ms)
- Active-line highlighting in CodeMirror while stepping
- Separate landing / levels / complete pages from the design (we have an inline
  overlay + breadcrumb instead — the lesson page is the whole app)
- `if` / `while` control flow in the DSL
- String concat in `System.out.println`
- More int ops beyond `+ - * /` (e.g., `%`)
- Visibility / volatile level — needs a memory model in the simulator (the
  current deterministic round-robin doesn't model JMM reordering)
- Structured concurrency level (`StructuredTaskScope`) — needs scope/group concept
- A "Sleep(N)" instruction and time-based pacing — currently every instruction
  takes one tick; some levels would benefit from explicit "this takes longer"
- Make the success overlay use level-provided copy via `LevelSpec` (currently
  hardcoded switch in `Main`)
