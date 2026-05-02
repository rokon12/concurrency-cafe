// Shared top navigation bar for Concurrency Café

function TopBar({ active, onThemeToggle, theme }) {
  const links = [
    { id: 'home', label: 'Home', href: 'index.html' },
    { id: 'levels', label: 'Levels', href: 'levels.html' },
    { id: 'reference', label: 'Reference', href: '#' },
    { id: 'book', label: 'Book', href: '#book' },
  ];
  return (
    <div className="topbar">
      <a href="index.html" className="brand" style={{ textDecoration: 'none', color: 'inherit' }}>
        <span className="brand-mark">cc</span>
        <span>Concurrency Café</span>
      </a>
      <nav className="nav">
        {links.map(l => (
          <a key={l.id} href={l.href} className={active === l.id ? 'active' : ''}>{l.label}</a>
        ))}
      </nav>
      <div className="spacer" />
      <div className="right">
        <a href="#book" className="ghost-btn" style={{ textDecoration: 'none' }}>
          📖&nbsp; Modern Concurrency in Java
        </a>
        <button className="ghost-btn" onClick={onThemeToggle} title="Toggle theme">
          {theme === 'dark' ? '☾' : '☀'}
        </button>
      </div>
    </div>
  );
}

window.TopBar = TopBar;
