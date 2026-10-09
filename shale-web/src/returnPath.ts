import { isSafeReturnRoute, routes } from './app/routeRegistry';

// Router-relative paths: App uses BrowserRouter without a basename; Vite uses its root base.
const defaultPath = routes.myShale.path;

export function redirectPathFrom(state: unknown): string {
  if (!state || typeof state !== 'object' || !('from' in state)) return defaultPath;
  const from = state.from;
  if (!from || typeof from !== 'object' || !('pathname' in from)) return defaultPath;
  const { pathname, search = '', hash = '' } = from as { pathname?: unknown; search?: unknown; hash?: unknown };
  if (typeof pathname !== 'string' || typeof search !== 'string' || typeof hash !== 'string') return defaultPath;
  if (!pathname.startsWith('/') || pathname.startsWith('//') || /[?#]/.test(pathname)) return defaultPath;
  if ((search && !search.startsWith('?')) || search.includes('#') || (hash && !hash.startsWith('#'))) return defaultPath;

  try {
    // Reject malformed escapes, control characters, backslashes and ambiguous path normalization.
    for (const part of [pathname, search, hash]) {
      if (/[\s\\]/.test(part) || /[\u0000-\u001f\u007f\\]/.test(decodeURIComponent(part))) return defaultPath;
    }
    if (/%2f|%5c/i.test(pathname)) return defaultPath;
    const decodedPath = decodeURIComponent(pathname);
    if (decodedPath.split('/').some(segment => segment === '.' || segment === '..')) return defaultPath;
    const url = new URL(pathname + search + hash, 'https://shale.invalid');
    if (url.origin !== 'https://shale.invalid' || url.pathname !== pathname) return defaultPath;
    if (!isSafeReturnRoute(pathname)) return defaultPath;
    return pathname + search + hash;
  } catch {
    return defaultPath;
  }
}
