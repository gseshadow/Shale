import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { activeDestination, destinations } from './navigation';

export function ResponsiveShell({ children, path, navigationKey = path, linkTo, utilities, identity, caption }: {
  children: ReactNode; path: string; navigationKey?: string; linkTo?: (path: string) => string; utilities?: ReactNode; identity: ReactNode; caption?: ReactNode;
}) {
  const disclosure = useRef<HTMLDetailsElement>(null);
  const summary = useRef<HTMLElement>(null);
  const main = useRef<HTMLElement>(null);
  const previousNavigation = useRef<string | undefined>(undefined);
  const [isCompact, setCompact] = useState(() => !window.matchMedia('(min-width: 48rem)').matches);
  useEffect(() => {
    const query = window.matchMedia('(min-width: 48rem)');
    const update = () => setCompact(!query.matches);
    query.addEventListener('change', update);
    return () => query.removeEventListener('change', update);
  }, []);
  useEffect(() => {
    if (disclosure.current) {
      if (isCompact && disclosure.current.contains(document.activeElement)) summary.current?.focus();
      disclosure.current.open = !isCompact;
    }
  }, [isCompact]);
  useEffect(() => {
    if (previousNavigation.current !== navigationKey) {
      previousNavigation.current = navigationKey;
      if (isCompact && disclosure.current) disclosure.current.open = false;
      main.current?.focus({ preventScroll: true });
    }
  }, [navigationKey, isCompact]);
  const current = activeDestination(path);
  return <div className="shale-shell">
    <a className="shale-skip" href="#foundation-main" onClick={() => main.current?.focus()}>Skip to content</a>
    <header className="shale-shell-header"><Link className="shale-brand" to={linkTo?.('/my-shale') ?? '/my-shale'} aria-label="Shale home">
      <span className="shale-brand-mark" aria-hidden="true">S</span>Shale</Link>
      {caption && <span className="shale-shell-caption">{caption}</span>}<div className="shale-shell-utilities">{utilities}</div>
    </header>
    <nav className="shale-shell-navigation" aria-label="Primary navigation">
      <details ref={disclosure} onKeyDown={event => {
        if (isCompact && event.key === 'Escape' && disclosure.current?.open) {
          event.preventDefault(); disclosure.current.open = false; summary.current?.focus();
        }
      }}>
        <summary ref={summary}>Navigation <span>{current?.label ?? 'My Shale'}</span></summary>
        <div className="shale-destinations">{destinations.map(item => !item.available && !linkTo
          ? <span key={item.path} className="shale-destination shale-destination-unavailable"><span>{item.label}</span><small> Unavailable</small></span>
          : <Link key={item.path} to={linkTo?.(item.path) ?? item.path}
          className={`shale-destination${current?.path === item.path ? ' shale-destination-active' : ''}`}
          aria-label={item.available ? item.label : `${item.label} Unavailable`}
          aria-current={current?.path === item.path ? 'page' : undefined}>
          <span>{item.label}</span>{!item.available && <small>{' '}Unavailable</small>}
        </Link>)}</div>
      </details>
      <div className="shale-shell-identity">{identity}</div>
    </nav>
    <main ref={main} id="foundation-main" tabIndex={-1} className="shale-shell-main">{children}</main>
  </div>;
}
