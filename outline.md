Good choice. I would use TeaVM for the **playable browser simulator**, not for pretending the browser is a full JVM. That gives you a Java-first web game while keeping the serious JVM concurrency examples in the book repo.

TeaVM compiles Java bytecode to JavaScript, and it can also target WebAssembly GC. It works from compiled `.class` / `.jar` files rather than Java source directly. That is nice for us because the game engine can be written in normal Java and compiled for the browser. The important caveat is that TeaVM is not a drop-in browser port for any arbitrary Java application. Its docs explicitly warn about constrained APIs such as reflection, class loaders, resources, and JNI, so we should write the browser game with TeaVM constraints in mind. ([TeaVM][1])

## The right architecture

I would split the project like this:

```text
concurrency-cafe/
  src/main/java/cafe/core/
    GameEngine.java
    LostUpdateLevel.java
    DeadlockLevel.java
    Scheduler.java
    EventLog.java
    Result.java

  src/main/java/cafe/web/
    Main.java
    Browser.java

  src/main/webapp/
    index.html
    style.css

  src/test/java/cafe/core/
    LostUpdateLevelTest.java

  jvm-exercises/
    real Java concurrency examples
    ExecutorService
    ReentrantLock
    Semaphore
    AtomicInteger
    virtual threads
```

The key idea:

```text
cafe.core = pure Java teaching simulator
cafe.web  = tiny TeaVM browser adapter
jvm-exercises = real JVM concurrency code for the book
```

That separation matters. The web game teaches the idea visually. The book exercises prove it on the real JVM.

## Use TeaVM JavaScript target first

TeaVM supports both `js` and `wasmGC` build targets through its Gradle plugin. For v0.1, I would start with the JavaScript target because it is simpler to load, debug, and host. TeaVM’s own Gradle docs list `generateJavaScript`, `javaScriptDevServer`, `generateWasmGC`, and `buildWasmGC` as separate build tasks, so you can add WebAssembly later without changing the whole project. ([TeaVM][2])

For the first public version, build this:

```text
Level 1: Lost Update

Buttons:
  Run Unsafe Counter
  Run Atomic Counter

Output:
  Expected: 43
  Actual: 42
  Bug detected: Lost update

Event log:
  Chef-1 reads completedOrders = 41
  Chef-2 reads completedOrders = 41
  Chef-1 writes completedOrders = 42
  Chef-2 writes completedOrders = 42
```

This is perfect for TeaVM because the simulator can create deterministic interleavings. You do not need actual nondeterministic race conditions in the browser.

## Build file

Use TeaVM’s Gradle plugin.

```groovy
plugins {
    id "java"
    id "war"
    id "org.teavm" version "0.13.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation teavm.libs.jsoApis
}

teavm {
    all {
        mainClass = "cafe.web.Main"
    }

    js {
        addedToWebApp = true
        targetFileName = "cafe.js"
        sourceMap = true
        obfuscated = false
    }
}
```

TeaVM’s docs show this same basic structure: Java plugin, WAR plugin, TeaVM plugin, `mainClass`, `js`, `wasmGC`, and `addedToWebApp`. The docs also show that the generated JavaScript is included in the web app under a `js/` path when using this setup. ([TeaVM][3])

## HTML page

Create:

```text
src/main/webapp/index.html
```

```html
<!doctype html>
<html>
  <head>
    <meta charset="utf-8">
    <title>Concurrency Café</title>
    <link rel="stylesheet" href="style.css">
    <script src="js/cafe.js"></script>
  </head>

  <body onload="main()">
    <main class="page">
      <h1>Concurrency Café</h1>
      <p class="subtitle">Level 1: Lost Update</p>

      <section class="controls">
        <button id="runUnsafe">Run Unsafe Counter</button>
        <button id="runAtomic">Run Atomic Counter</button>
      </section>

      <section class="panel">
        <h2>Metrics</h2>
        <div id="metrics"></div>
      </section>

      <section class="panel">
        <h2>Kitchen Event Log</h2>
        <div id="eventLog"></div>
      </section>

      <section class="panel">
        <h2>Java lesson</h2>
        <pre><code id="codePanel"></code></pre>
      </section>
    </main>
  </body>
</html>
```

## Tiny browser bridge

Create:

```text
src/main/java/cafe/web/Browser.java
```

```java
package cafe.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

public final class Browser {
    private Browser() {
    }

    @JSFunctor
    public interface Callback extends JSObject {
        void run();
    }

    @JSBody(
        params = { "id", "html" },
        script = "document.getElementById(id).innerHTML = html;"
    )
    public static native void setHtml(String id, String html);

    @JSBody(
        params = { "id", "text" },
        script = "document.getElementById(id).textContent = text;"
    )
    public static native void setText(String id, String text);

    @JSBody(
        params = { "id", "callback" },
        script = "document.getElementById(id).addEventListener('click', callback);"
    )
    public static native void onClick(String id, Callback callback);
}
```

This uses TeaVM’s JavaScript interop model. TeaVM supports `@JSBody` for embedding JavaScript calls, and it supports `@JSFunctor` for passing Java objects as JavaScript callback functions. ([TeaVM][4])

## Core simulation model

Create:

```text
src/main/java/cafe/core/Result.java
```

```java
package cafe.core;

import java.util.List;

public final class Result {
    private final String strategy;
    private final int expected;
    private final int actual;
    private final List<String> events;
    private final String code;

    public Result(
        String strategy,
        int expected,
        int actual,
        List<String> events,
        String code
    ) {
        this.strategy = strategy;
        this.expected = expected;
        this.actual = actual;
        this.events = List.copyOf(events);
        this.code = code;
    }

    public String strategy() {
        return strategy;
    }

    public int expected() {
        return expected;
    }

    public int actual() {
        return actual;
    }

    public List<String> events() {
        return events;
    }

    public String code() {
        return code;
    }

    public boolean passed() {
        return expected == actual;
    }

    public int lostUpdates() {
        return expected - actual;
    }
}
```

Now the first level:

```text
src/main/java/cafe/core/LostUpdateLevel.java
```

```java
package cafe.core;

import java.util.ArrayList;
import java.util.List;

public final class LostUpdateLevel {
    public Result runUnsafeCounter() {
        List<String> events = new ArrayList<>();

        int completedOrders = 41;

        int chef1Read = completedOrders;
        events.add("Chef-1 reads completedOrders = " + chef1Read);

        int chef2Read = completedOrders;
        events.add("Chef-2 reads completedOrders = " + chef2Read);

        completedOrders = chef1Read + 1;
        events.add("Chef-1 writes completedOrders = " + completedOrders);

        completedOrders = chef2Read + 1;
        events.add("Chef-2 writes completedOrders = " + completedOrders);

        events.add("Bug detected: two increments collapsed into one.");

        return new Result(
            "Unsafe Counter",
            43,
            completedOrders,
            events,
            """
            // Broken idea:
            completedOrders++;

            // This is really:
            // 1. read completedOrders
            // 2. add one
            // 3. write completedOrders
            """
        );
    }

    public Result runAtomicCounter() {
        List<String> events = new ArrayList<>();

        int completedOrders = 41;

        completedOrders = atomicIncrement(completedOrders);
        events.add("Chef-1 atomically increments completedOrders to " + completedOrders);

        completedOrders = atomicIncrement(completedOrders);
        events.add("Chef-2 atomically increments completedOrders to " + completedOrders);

        events.add("No lost update: each increment happened as one indivisible operation.");

        return new Result(
            "Atomic Counter",
            43,
            completedOrders,
            events,
            """
            // JVM version:
            AtomicInteger completedOrders = new AtomicInteger();

            completedOrders.incrementAndGet();
            """
        );
    }

    private int atomicIncrement(int value) {
        return value + 1;
    }
}
```

Notice the deliberate design: the TeaVM game does **not** need to use `AtomicInteger` internally to prove atomicity. It is a visual simulator. The real `AtomicInteger` exercise belongs in the JVM exercise folder.

## TeaVM entry point

Create:

```text
src/main/java/cafe/web/Main.java
```

```java
package cafe.web;

import cafe.core.LostUpdateLevel;
import cafe.core.Result;

public final class Main {
    private static final LostUpdateLevel LEVEL = new LostUpdateLevel();

    public static void main(String[] args) {
        Browser.onClick("runUnsafe", Main::runUnsafe);
        Browser.onClick("runAtomic", Main::runAtomic);

        renderIntro();
    }

    private static void runUnsafe() {
        render(LEVEL.runUnsafeCounter());
    }

    private static void runAtomic() {
        render(LEVEL.runAtomicCounter());
    }

    private static void renderIntro() {
        Browser.setHtml("metrics", """
            <p>Choose a strategy and run the level.</p>
            <p>The goal is to make the completed order count correct.</p>
            """
        );

        Browser.setHtml("eventLog", """
            <p>No events yet.</p>
            """
        );

        Browser.setText("codePanel", """
            Try the unsafe counter first.

            Then compare it with the atomic counter strategy.
            """);
    }

    private static void render(Result result) {
        String status = result.passed() ? "✅ Correct" : "❌ Bug detected";

        Browser.setHtml("metrics", """
            <p><strong>Strategy:</strong> %s</p>
            <p><strong>Expected:</strong> %d</p>
            <p><strong>Actual:</strong> %d</p>
            <p><strong>Lost updates:</strong> %d</p>
            <p><strong>Status:</strong> %s</p>
            """.formatted(
                escape(result.strategy()),
                result.expected(),
                result.actual(),
                result.lostUpdates(),
                status
            )
        );

        StringBuilder log = new StringBuilder("<ol>");
        for (String event : result.events()) {
            log.append("<li>")
                .append(escape(event))
                .append("</li>");
        }
        log.append("</ol>");

        Browser.setHtml("eventLog", log.toString());
        Browser.setText("codePanel", result.code());
    }

    private static String escape(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
```

## Styling

Create:

```text
src/main/webapp/style.css
```

```css
body {
  font-family: system-ui, sans-serif;
  margin: 0;
  background: #f7f2e8;
  color: #252018;
}

.page {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px;
}

.subtitle {
  font-size: 1.1rem;
  opacity: 0.8;
}

.controls {
  display: flex;
  gap: 12px;
  margin: 24px 0;
}

button {
  font-size: 1rem;
  padding: 10px 14px;
  border: 1px solid #8b7355;
  border-radius: 8px;
  background: #fffaf0;
  cursor: pointer;
}

button:hover {
  background: #fff0c9;
}

.panel {
  background: white;
  border: 1px solid #e1d3bd;
  border-radius: 12px;
  padding: 18px;
  margin: 16px 0;
}

pre {
  white-space: pre-wrap;
  background: #1f1f1f;
  color: #f5f5f5;
  padding: 16px;
  border-radius: 8px;
}
```

## Run it locally

Build it:

```bash
./gradlew build
```

TeaVM’s getting-started docs say the Gradle setup can produce a `.war` file that you can deploy to a servlet container, or unzip and open the generated `index.html`. For development, TeaVM also provides a `javaScriptDevServer` task that serves generated JavaScript with rebuild support. ([TeaVM][3])

For a practical static-site workflow:

```bash
./gradlew build
mkdir -p dist
cd dist
jar -xf ../build/libs/concurrency-cafe.war
python3 -m http.server 8080
```

Then open:

```text
http://localhost:8080
```

For the JavaScript target, this is enough. If you later use WebAssembly GC, serve it over HTTP. TeaVM’s docs note that browsers do not load `wasm` files from the local filesystem, so Wasm builds should be served by something like a local HTTP server. ([TeaVM][5])

## What TeaVM gives us for the game

TeaVM is especially interesting here because it supports Java-style threads through coroutines. Its docs say TeaVM uses coroutines to emulate `Thread` and some simple synchronization primitives, including `synchronized`, `Object.wait`, and `Object.notify`. ([TeaVM][6])

But I would still keep the teaching simulator deterministic. TeaVM’s own overview describes its browser threads as green threads, meaning multiple logical threads on one physical browser thread. That is useful for animation and storytelling, but it is not the same thing as running a real Java server under platform threads or virtual threads. ([TeaVM][1])

So the rule should be:

```text
Browser game:
  show the concept
  control the schedule
  explain the bug visually

JVM exercises:
  run the real concurrency primitive
  test with JUnit
  benchmark when useful
```

For example, the browser level can teach “blocking supplier call with many chefs.” The JVM exercise can then use actual virtual threads. Oracle describes virtual threads as lightweight threads intended to reduce the effort of writing, maintaining, and debugging high-throughput concurrent applications, especially when many tasks spend time waiting. ([Oracle Docs][7])

## The first milestone

Build only this first:

```text
Concurrency Café TeaVM v0.1

One page
One level: Lost Update
Two buttons: unsafe and atomic
Metrics panel
Event log panel
Java code panel
No animation yet
No Canvas yet
No WebAssembly yet
```

Then v0.2:

```text
Add animation:
  Chef-1 read
  Chef-2 read
  Chef-1 write
  Chef-2 write

Add delay between steps:
  show the bad interleaving frame by frame
```

Then v0.3:

```text
Add Level 2:
  Shared Order Board

Strategies:
  Unsafe HashMap
  synchronized board
  ConcurrentHashMap-style board
  single-writer board
```

Then v0.4:

```text
Add Level 3:
  Deadlock Kitchen

Resources:
  oven
  fryer

Bug:
  Chef-1 holds oven, waits for fryer
  Chef-2 holds fryer, waits for oven
```

My recommendation: use TeaVM’s **JavaScript target** for the first playable site, keep the UI bridge tiny, keep the simulator in pure Java, and keep real modern Java concurrency examples in a separate JVM exercise module. That gives you a browser game that feels native to your Java book without turning the browser into a fake JVM.

[1]: https://teavm.org/docs/intro/overview.html "TeaVM  — Overview"
[2]: https://teavm.org/docs/tooling/gradle.html "TeaVM  — Using Gradle"
[3]: https://teavm.org/docs/intro/getting-started.html?utm_source=chatgpt.com "Getting started"
[4]: https://teavm.org/docs/runtime/jso.html "TeaVM  — Interacting with JavaScript"
[5]: https://teavm.org/docs/intro/getting-started.html "TeaVM  — Getting started"
[6]: https://teavm.org/docs/runtime/coroutines.html "TeaVM  — Coroutines"
[7]: https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html?utm_source=chatgpt.com "Virtual Threads"
