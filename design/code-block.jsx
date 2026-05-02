// Minimal Java syntax highlighter — produces React nodes from a source string.

const JAVA_KEYWORDS = new Set([
  'class','public','private','protected','static','final','void','new','return',
  'if','else','for','while','do','switch','case','break','continue','try','catch',
  'finally','throw','throws','extends','implements','interface','enum','package',
  'import','this','super','null','true','false','synchronized','volatile','abstract',
  'instanceof'
]);
const JAVA_TYPES = new Set([
  'int','long','short','byte','double','float','char','boolean','String',
  'Object','Thread','Runnable','AtomicInteger','ReentrantLock','Lock'
]);

function tokenizeJava(src) {
  const out = [];
  let i = 0;
  while (i < src.length) {
    const c = src[i];
    // line comment
    if (c === '/' && src[i + 1] === '/') {
      const j = src.indexOf('\n', i);
      const end = j === -1 ? src.length : j;
      out.push({ t: 'c', v: src.slice(i, end) });
      i = end;
      continue;
    }
    // string
    if (c === '"') {
      let j = i + 1;
      while (j < src.length && src[j] !== '"') { if (src[j] === '\\') j++; j++; }
      out.push({ t: 's', v: src.slice(i, j + 1) });
      i = j + 1;
      continue;
    }
    // number
    if (/[0-9]/.test(c)) {
      let j = i;
      while (j < src.length && /[0-9.]/.test(src[j])) j++;
      out.push({ t: 'n', v: src.slice(i, j) });
      i = j;
      continue;
    }
    // identifier
    if (/[A-Za-z_$]/.test(c)) {
      let j = i;
      while (j < src.length && /[A-Za-z0-9_$]/.test(src[j])) j++;
      const word = src.slice(i, j);
      let next = src[j];
      // skip whitespace to find next non-space char
      let k = j;
      while (k < src.length && /\s/.test(src[k])) k++;
      const after = src[k];
      let t = 'i';
      if (JAVA_KEYWORDS.has(word)) t = 'k';
      else if (JAVA_TYPES.has(word)) t = 't';
      else if (after === '(') t = 'fn';
      out.push({ t, v: word });
      i = j;
      continue;
    }
    // newline
    if (c === '\n') {
      out.push({ t: 'nl', v: '\n' });
      i++;
      continue;
    }
    // punctuation/whitespace
    out.push({ t: c.trim() === '' ? 'ws' : 'p', v: c });
    i++;
  }
  return out;
}

function highlightJava(src) {
  const tokens = tokenizeJava(src);
  const lines = [[]];
  for (const tk of tokens) {
    if (tk.t === 'nl') { lines.push([]); continue; }
    lines[lines.length - 1].push(tk);
  }
  return lines.map((line, idx) => (
    <div key={idx} className="code-line">
      <span className="ln">{idx + 1}</span>
      {line.map((tk, j) => {
        const cls = tk.t === 'i' || tk.t === 'p' || tk.t === 'ws' ? '' : 'tok-' + tk.t;
        return <span key={j} className={cls}>{tk.v}</span>;
      })}
    </div>
  ));
}

function CodeBlock({ src, highlightLine, style }) {
  const lines = highlightJava(src);
  return (
    <div className="code scrollbar" style={{ overflow: 'auto', ...style }}>
      {lines.map((node, i) => (
        <div
          key={i}
          style={{
            background: highlightLine === i + 1 ? 'color-mix(in oklab, var(--accent) 18%, transparent)' : 'transparent',
            paddingLeft: 8,
            borderLeft: highlightLine === i + 1 ? '2px solid var(--accent)' : '2px solid transparent',
          }}
        >
          {node.props.children}
        </div>
      ))}
    </div>
  );
}

window.CodeBlock = CodeBlock;
window.highlightJava = highlightJava;
window.tokenizeJava = tokenizeJava;
