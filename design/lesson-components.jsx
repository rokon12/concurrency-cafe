// Lesson sub-components

function LessonHeader({ level, total, title, chapter, passed, attempted }) {
  return (
    <div className="lesson-header">
      <div className="lh-left">
        <a href="levels.html" className="back-link">← All levels</a>
        <div className="lh-meta">
          <span className="chapter-tag">Chapter I · {chapter}</span>
          <span className="dot-sep">·</span>
          <span className="level-num">Level {String(level).padStart(2, '0')} of {String(total).padStart(2, '0')}</span>
        </div>
        <h1 className="lh-title">{title}</h1>
        <p className="lh-goal">
          Two chefs increment a shared counter starting at <code>41</code>. After both finish, it should equal <code>43</code>.
        </p>
      </div>
      <div className="lh-right">
        <div className="objective-card">
          <div className="oc-head">Objective</div>
          <div className="oc-state">
            {!attempted && <><span className="pill muted">untested</span><span>Run the code to see the bug.</span></>}
            {attempted && !passed && <><span className="pill err">FAIL</span><span>One increment was lost. Fix it.</span></>}
            {attempted && passed && <><span className="pill ok">PASS</span><span>Both increments stuck. Nicely done.</span></>}
          </div>
          <div className="oc-progress">
            <div className="oc-step">
              <span className={`oc-dot ${attempted ? 'on' : ''}`} />
              <span>Run starter</span>
            </div>
            <div className="oc-step">
              <span className={`oc-dot ${passed ? 'on' : ''}`} />
              <span>Counter ends at 43</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function CodeEditor({ value, onChange, highlightLine }) {
  const taRef = React.useRef(null);
  const lines = value.split('\n');

  return (
    <div className="editor-wrap">
      <div className="editor-gutter">
        {lines.map((_, i) => (
          <div
            key={i}
            className={highlightLine === i + 1 ? 'gutter-line on' : 'gutter-line'}
          >
            {i + 1}
          </div>
        ))}
      </div>
      <div className="editor-stack">
        <div className="editor-highlight">
          {lines.map((line, i) => (
            <div
              key={i}
              className={'eh-line' + (highlightLine === i + 1 ? ' on' : '')}
            >
              <SyntaxLine src={line} />
            </div>
          ))}
        </div>
        <textarea
          ref={taRef}
          className="editor-textarea scrollbar"
          spellCheck={false}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Tab') {
              e.preventDefault();
              const ta = e.target;
              const s = ta.selectionStart, en = ta.selectionEnd;
              const newVal = ta.value.slice(0, s) + '  ' + ta.value.slice(en);
              onChange(newVal);
              setTimeout(() => { ta.selectionStart = ta.selectionEnd = s + 2; }, 0);
            }
          }}
        />
      </div>
    </div>
  );
}

function SyntaxLine({ src }) {
  // Tokenize a single line using the global tokenizer
  if (!src) return <>{'\u00A0'}</>;
  const tokens = (window.tokenizeJava || tokenizeJavaInline)(src);
  return (
    <>
      {tokens.map((tk, i) => {
        if (tk.t === 'nl') return null;
        const cls = tk.t === 'i' || tk.t === 'p' || tk.t === 'ws' ? '' : 'tok-' + tk.t;
        return <span key={i} className={cls}>{tk.v}</span>;
      })}
    </>
  );
}

// Inline fallback tokenizer (mirrors code-block.jsx)
const _JK = new Set(['class','public','private','protected','static','final','void','new','return','if','else','for','while','do','switch','case','break','continue','try','catch','finally','throw','throws','extends','implements','interface','enum','package','import','this','super','null','true','false','synchronized','volatile','abstract','instanceof']);
const _JT = new Set(['int','long','short','byte','double','float','char','boolean','String','Object','Thread','Runnable','AtomicInteger','ReentrantLock','Lock']);
function tokenizeJavaInline(src) {
  const out = []; let i = 0;
  while (i < src.length) {
    const c = src[i];
    if (c === '/' && src[i+1] === '/') { out.push({ t: 'c', v: src.slice(i) }); break; }
    if (c === '"') {
      let j = i+1; while (j<src.length && src[j]!=='"') { if (src[j]==='\\') j++; j++; }
      out.push({ t: 's', v: src.slice(i, j+1) }); i = j+1; continue;
    }
    if (/[0-9]/.test(c)) { let j=i; while(j<src.length && /[0-9.]/.test(src[j])) j++; out.push({t:'n',v:src.slice(i,j)}); i=j; continue; }
    if (/[A-Za-z_$]/.test(c)) {
      let j=i; while(j<src.length && /[A-Za-z0-9_$]/.test(src[j])) j++;
      const w = src.slice(i,j);
      let k=j; while(k<src.length && /\s/.test(src[k])) k++;
      const after = src[k];
      let t='i';
      if (_JK.has(w)) t='k'; else if (_JT.has(w)) t='t'; else if (after==='(') t='fn';
      out.push({t,v:w}); i=j; continue;
    }
    out.push({ t: c.trim()===''?'ws':'p', v: c }); i++;
  }
  return out;
}

function KitchenViz({ counter, chef1, chef2, synchronized: syn, step, trace }) {
  const lockHolder = React.useMemo(() => {
    if (!syn || step < 0) return null;
    let holder = null;
    for (let i = 0; i <= step; i++) {
      const t = trace[i];
      if (t.kind === 'lock') holder = t.actor;
      if (t.kind === 'unlock') holder = null;
    }
    return holder;
  }, [step, trace, syn]);

  return (
    <div className="kitchen">
      <ChefCard name="chef1" color="var(--chef-1)" state={chef1} hasLock={lockHolder === 'chef1'} />
      <div className="counter-stage">
        {syn && (
          <div className={`lock-icon ${lockHolder ? 'held' : ''}`}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="5" y="11" width="14" height="10" rx="2" />
              <path d="M8 11V7a4 4 0 0 1 8 0v4" />
            </svg>
            <span>{lockHolder ? `held by ${lockHolder}` : 'lock free'}</span>
          </div>
        )}
        <div className="counter-label">int counter</div>
        <div className="counter-display">{counter}</div>
        <div className="counter-target">target = 43</div>
      </div>
      <ChefCard name="chef2" color="var(--chef-2)" state={chef2} hasLock={lockHolder === 'chef2'} />
    </div>
  );
}

function ChefCard({ name, color, state, hasLock }) {
  const statusLabel = {
    idle: 'idle',
    read: 'reading',
    write: 'writing',
    lock: 'locking',
    unlock: 'unlocking',
  }[state.status] || state.status;
  return (
    <div className="chef-card" style={{ borderColor: state.status !== 'idle' ? color : 'var(--line)' }}>
      <div className="chef-card-head" style={{ color }}>
        <span className="chef-dot" style={{ background: color }} />
        <span>{name}</span>
        {hasLock && <span className="chef-lock-tag">🔒 holds lock</span>}
      </div>
      <div className="chef-status">
        <span className="status-label">status</span>
        <span className="status-val">{statusLabel}</span>
      </div>
      <div className="chef-status">
        <span className="status-label">local x</span>
        <span className="status-val mono">{state.holds === undefined ? '—' : state.holds}</span>
      </div>
      <div className="chef-action">{state.text}</div>
    </div>
  );
}

function EventLog({ log }) {
  if (!log.length) {
    return <div className="empty-log">Run the code to see the kitchen log.</div>;
  }
  return (
    <ol className="event-log">
      {log.map((e, i) => (
        <li key={i} className={`ev ev-${e.actor} ev-${e.kind}`}>
          <span className="ev-num">{String(i + 1).padStart(2, '0')}</span>
          <span className="ev-actor" style={{ color: e.actor === 'chef1' ? 'var(--chef-1)' : 'var(--chef-2)' }}>
            {e.actor}
          </span>
          <span className="ev-kind">{e.kind}</span>
          <span className="ev-text">{e.text.replace(e.actor + ' ', '')}</span>
        </li>
      ))}
    </ol>
  );
}

function ResultBar({ counter, attempted, done, passed }) {
  if (!attempted) {
    return (
      <div className="result-bar idle">
        <span className="pill muted">idle</span>
        <span>counter = {counter} · run the code to begin</span>
      </div>
    );
  }
  return (
    <div className={`result-bar ${passed && done ? 'pass' : 'fail'}`}>
      {done && passed && <><span className="pill ok">PASS</span><span><b>counter = 43</b> · both increments stuck</span></>}
      {done && !passed && <><span className="pill err">FAIL</span><span><b>counter = {counter}</b>, expected 43 · 1 update lost</span></>}
      {!done && <><span className="pill warn">running</span><span>counter = {counter}</span></>}
    </div>
  );
}

function BookCTA() {
  return (
    <a className="aux-card book-cta" href="index.html#book">
      <div className="book-mini">
        <div className="book-mini-cover">
          <span>cc</span>
        </div>
      </div>
      <div className="book-cta-body">
        <div className="book-cta-eyebrow">Companion book</div>
        <div className="book-cta-title">Modern Concurrency in Java</div>
        <div className="book-cta-desc">320 pages on the JMM, virtual threads, structured concurrency.</div>
      </div>
      <div className="book-cta-arrow">→</div>
    </a>
  );
}

function LessonFooter({ passed, onNext }) {
  return (
    <div className="lesson-footer">
      <a href="levels.html" className="btn ghost">← All levels</a>
      <div style={{ flex: 1 }} />
      <span className="footer-note">
        {passed
          ? "Solved. The bug is dead, long live the bug."
          : "Stuck? Open the hint or reference panel below."}
      </span>
      <button className="btn" disabled>← Previous level</button>
      <button className={`btn ${passed ? 'primary' : ''}`} disabled={!passed} onClick={onNext}>
        Next level →
      </button>
    </div>
  );
}

function SuccessOverlay({ onClose }) {
  return (
    <div className="overlay" onClick={onClose}>
      <div className="overlay-card" onClick={(e) => e.stopPropagation()}>
        <div className="overlay-eyebrow">Level 01 complete</div>
        <h2>You sealed the race.</h2>
        <p>
          Synchronizing the read-modify-write makes it atomic — no other thread can sneak between the read and the write. Next up: <b>AtomicInteger</b>, which gives you the same guarantee without an explicit lock.
        </p>
        <div className="overlay-actions">
          <button className="btn ghost" onClick={onClose}>Stay here</button>
          <button className="btn primary" onClick={onClose}>Next: Atomic Counter →</button>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, {
  LessonHeader, CodeEditor, KitchenViz, ChefCard, EventLog,
  ResultBar, BookCTA, LessonFooter, SuccessOverlay, SyntaxLine,
  tokenizeJavaInline,
});
