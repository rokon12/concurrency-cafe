// Lesson page — Level 1: Lost Update

const LESSON = {
  level: 1,
  totalLevels: 8,
  title: 'Lost Update',
  chapter: 'Shared state',
  goal: 'Two chefs increment a shared counter that starts at 41. After both run, it should equal 43.',
  hint: "Both chefs read the counter before either has written. They each see 41, both write 42. One increment vanishes.",
  starterCode: `// counter starts at 41. Each chef serves one more order.
// Run this and watch what happens. Then make both increments stick.

Thread chef1 = Thread.ofVirtual().start(() -> {
  int x = counter;
  counter = x + 1;
});

Thread chef2 = Thread.ofVirtual().start(() -> {
  int x = counter;
  counter = x + 1;
});`,
  solutionCode: `Thread chef1 = Thread.ofVirtual().start(() -> {
  synchronized (counterLock) {
    int x = counter;
    counter = x + 1;
  }
});

Thread chef2 = Thread.ofVirtual().start(() -> {
  synchronized (counterLock) {
    int x = counter;
    counter = x + 1;
  }
});`,
};

const REFERENCE = [
  { sig: 'Thread.ofVirtual().start(() -> {…})', desc: 'start a virtual thread chef' },
  { sig: 'Thread.ofPlatform().start(() -> {…})', desc: 'start a platform thread chef' },
  { sig: 'int x = expr;   v += n', desc: 'declare / mutate a local' },
  { sig: 'AtomicInteger v.incrementAndGet()', desc: 'atomic add-and-fetch' },
  { sig: 'synchronized (m) {…}', desc: 'object monitor' },
  { sig: 'ReentrantLock l.lock() / l.unlock()', desc: 'explicit lock' },
  { sig: 'System.out.println("…")', desc: 'emit a log line' },
];

function LessonPage() {
  const [theme, setTheme] = React.useState('dark');
  const [code, setCode] = React.useState(LESSON.starterCode);
  const [running, setRunning] = React.useState(false);
  const [step, setStep] = React.useState(-1);
  const [counter, setCounter] = React.useState(41);
  const [log, setLog] = React.useState([]);
  const [hintOpen, setHintOpen] = React.useState(false);
  const [refOpen, setRefOpen] = React.useState(true);
  const [solved, setSolved] = React.useState(false);
  const [tab, setTab] = React.useState('viz'); // viz | log
  const intervalRef = React.useRef(null);

  React.useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  // Detect whether code uses synchronization
  const isSynchronized = /synchronized\s*\(|\.lock\s*\(\s*\)|incrementAndGet|getAndIncrement|AtomicInteger/.test(code);

  // Build interleaved trace based on synchronization
  const trace = React.useMemo(() => {
    if (isSynchronized) {
      // Sequential, both increments stick
      return [
        { actor: 'chef1', kind: 'lock', text: 'chef1 acquires lock' },
        { actor: 'chef1', kind: 'read', text: 'chef1 reads counter = 41', counter: 41, holds: 41 },
        { actor: 'chef1', kind: 'write', text: 'chef1 writes counter = 42', counter: 42 },
        { actor: 'chef1', kind: 'unlock', text: 'chef1 releases lock' },
        { actor: 'chef2', kind: 'lock', text: 'chef2 acquires lock' },
        { actor: 'chef2', kind: 'read', text: 'chef2 reads counter = 42', counter: 42, holds: 42 },
        { actor: 'chef2', kind: 'write', text: 'chef2 writes counter = 43', counter: 43 },
        { actor: 'chef2', kind: 'unlock', text: 'chef2 releases lock' },
      ];
    } else {
      return [
        { actor: 'chef1', kind: 'read', text: 'chef1 reads counter = 41', counter: 41, holds: 41 },
        { actor: 'chef2', kind: 'read', text: 'chef2 reads counter = 41', counter: 41, holds: 41 },
        { actor: 'chef1', kind: 'write', text: 'chef1 writes counter = 42', counter: 42 },
        { actor: 'chef2', kind: 'write', text: 'chef2 writes counter = 42', counter: 42 },
      ];
    }
  }, [isSynchronized]);

  const finalCounter = React.useMemo(() => {
    for (let i = trace.length - 1; i >= 0; i--) {
      if (trace[i].counter !== undefined) return trace[i].counter;
    }
    return 41;
  }, [trace]);
  const passed = finalCounter === 43;

  // Step engine
  const advance = React.useCallback(() => {
    setStep(s => {
      const next = s + 1;
      if (next >= trace.length) {
        if (intervalRef.current) { clearInterval(intervalRef.current); intervalRef.current = null; }
        setRunning(false);
        return s;
      }
      const t = trace[next];
      if (t.counter !== undefined) setCounter(t.counter);
      setLog(l => [...l, t]);
      if (next === trace.length - 1 && finalCounter === 43) {
        setSolved(true);
      }
      return next;
    });
  }, [trace]);

  const reset = () => {
    if (intervalRef.current) { clearInterval(intervalRef.current); intervalRef.current = null; }
    setRunning(false);
    setStep(-1);
    setCounter(41);
    setLog([]);
    setSolved(false);
  };

  const play = () => {
    reset();
    setTimeout(() => {
      setRunning(true);
      intervalRef.current = setInterval(advance, 700);
    }, 50);
  };

  const runToEnd = () => {
    reset();
    setTimeout(() => {
      // play out instantly
      setLog(trace);
      setCounter(finalCounter);
      setStep(trace.length - 1);
      if (finalCounter === 43) setSolved(true);
    }, 50);
  };

  React.useEffect(() => {
    if (step >= 0 && step === trace.length - 1 && finalCounter === 43) {
      setSolved(true);
    }
  }, [step, trace, finalCounter]);

  // Chef states for viz
  const chef1State = React.useMemo(() => buildChefState('chef1', log, step, trace), [log, step, trace]);
  const chef2State = React.useMemo(() => buildChefState('chef2', log, step, trace), [log, step, trace]);
  const currentLine = highlightedLine(code, step, trace);

  return (
    <>
      <TopBar active="levels" theme={theme} onThemeToggle={() => setTheme(t => t === 'dark' ? 'light' : 'dark')} />

      <LessonHeader
        level={LESSON.level} total={LESSON.totalLevels}
        title={LESSON.title} chapter={LESSON.chapter}
        passed={passed && step === trace.length - 1}
        attempted={step >= 0}
      />

      <div className="lesson-grid">
        {/* Left: code editor */}
        <div className="pane pane-code">
          <div className="pane-head">
            <span className="dot" style={{ background: 'var(--accent)' }} />
            <span>lost-update.java</span>
            <span className="pane-head-meta">{isSynchronized ? 'synchronized' : 'unsynchronized'}</span>
            <div style={{ flex: 1 }} />
            <button className="btn ghost sm" onClick={() => setCode(LESSON.starterCode)}>Reset</button>
            <button className="btn ghost sm" onClick={() => setCode(LESSON.solutionCode)}>Show solution</button>
          </div>
          <CodeEditor value={code} onChange={setCode} highlightLine={currentLine} />
          <div className="pane-foot">
            <button className="btn primary" onClick={runToEnd} disabled={running}>▶ Run to end</button>
            <button className="btn" onClick={play} disabled={running}>↻ Play stepwise</button>
            <button className="btn" onClick={advance} disabled={running || step >= trace.length - 1}>→ Step</button>
            <button className="btn ghost" onClick={reset}>Reset run</button>
            <div style={{ flex: 1 }} />
            <span className="run-meta">
              step {step + 1} / {trace.length}
            </span>
          </div>
        </div>

        {/* Right: visualization & log */}
        <div className="pane pane-sim">
          <div className="pane-head">
            <span className="dot" style={{ background: passed && step === trace.length - 1 ? 'var(--ok)' : 'var(--err)' }} />
            <span>kitchen</span>
            <div className="tabs">
              <button className={tab === 'viz' ? 'tab on' : 'tab'} onClick={() => setTab('viz')}>Visual</button>
              <button className={tab === 'log' ? 'tab on' : 'tab'} onClick={() => setTab('log')}>Event log</button>
            </div>
          </div>
          <div className="sim-body">
            {tab === 'viz' && (
              <KitchenViz
                counter={counter}
                chef1={chef1State}
                chef2={chef2State}
                synchronized={isSynchronized}
                step={step}
                trace={trace}
              />
            )}
            {tab === 'log' && <EventLog log={log} />}
          </div>
          <ResultBar
            counter={counter}
            attempted={step >= 0}
            done={step === trace.length - 1}
            passed={passed}
          />
        </div>
      </div>

      {/* Hint + Reference rail */}
      <div className="aux-rail">
        <details className="aux-card" open={hintOpen} onToggle={(e) => setHintOpen(e.target.open)}>
          <summary>
            <span className="aux-icon">💡</span>
            <span>Hint</span>
            <span className="aux-meta">tap to reveal</span>
          </summary>
          <div className="aux-body">
            <p>{LESSON.hint}</p>
            <p style={{ marginTop: 12 }}>
              The fix is to make the read-modify-write happen <em>atomically</em>. Try wrapping the body in <code>synchronized (counterLock) {'{ … }'}</code>, or replace <code>int counter</code> with an <code>AtomicInteger</code>.
            </p>
          </div>
        </details>

        <details className="aux-card" open={refOpen} onToggle={(e) => setRefOpen(e.target.open)}>
          <summary>
            <span className="aux-icon">📖</span>
            <span>Java reference</span>
            <span className="aux-meta">{REFERENCE.length} entries</span>
          </summary>
          <div className="aux-body">
            <table className="ref-table">
              <tbody>
                {REFERENCE.map((r, i) => (
                  <tr key={i}>
                    <td><code>{r.sig}</code></td>
                    <td>{r.desc}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </details>

        <BookCTA />
      </div>

      <LessonFooter
        passed={passed && step === trace.length - 1}
        onNext={() => alert('Level 2 coming up — virtual demo')}
      />

      {solved && <SuccessOverlay onClose={() => setSolved(false)} />}
    </>
  );
}

function buildChefState(name, log, step, trace) {
  const events = log.slice(0, step + 1).filter(e => e.actor === name);
  if (events.length === 0) return { status: 'idle', text: 'waiting' };
  const last = events[events.length - 1];
  return {
    status: last.kind,
    text: last.text.replace(name + ' ', ''),
    holds: last.holds,
  };
}

function highlightedLine(code, step, trace) {
  if (step < 0) return null;
  const t = trace[step];
  if (!t) return null;
  // crude: locate based on actor + kind
  const lines = code.split('\n');
  const findFor = (actor, pattern) => {
    let inBlock = false;
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].includes(actor)) inBlock = true;
      if (inBlock && pattern.test(lines[i])) return i + 1;
      if (inBlock && lines[i].includes('});')) inBlock = false;
    }
    return null;
  };
  if (t.kind === 'read') return findFor(t.actor, /int\s+x\s*=\s*counter/);
  if (t.kind === 'write') return findFor(t.actor, /counter\s*=\s*x/);
  if (t.kind === 'lock') return findFor(t.actor, /synchronized|\.lock\(/);
  if (t.kind === 'unlock') return findFor(t.actor, /\}\)|unlock/);
  return null;
}

window.LessonPage = LessonPage;
