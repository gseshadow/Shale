import { FormEvent, useMemo, useState } from 'react';
import { AuthenticatedUser, apiBaseUrl, getCurrentUser, login, storeAccessToken } from './api';
import './styles.css';

function displayNameFor(user: AuthenticatedUser): string {
  return user.displayName || [user.nameFirst, user.nameLast].filter(Boolean).join(' ') || user.email || `User ${user.userId}`;
}

export default function App() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [user, setUser] = useState<AuthenticatedUser | null>(null);
  const [tokenPreview, setTokenPreview] = useState<string | null>(null);
  const baseUrl = useMemo(() => apiBaseUrl(), []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setUser(null);
    setTokenPreview(null);
    setIsSubmitting(true);

    try {
      const result = await login(email, password);
      storeAccessToken(result.accessToken);
      const verifiedUser = await getCurrentUser(result.accessToken);
      setUser(verifiedUser);
      setTokenPreview(`${result.tokenType} ${result.accessToken.slice(0, 16)}…`);
      setPassword('');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Login failed.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="app-shell">
      <section className="hero-card">
        <p className="eyebrow">Shale Web</p>
        <h1>Browser login for the deployed Azure API</h1>
        <p className="lede">This standalone React + TypeScript + Vite app validates the first web milestone without changing the existing JavaFX/Maven desktop app.</p>
        <p className="api-note">API target: <code>{baseUrl}</code></p>
      </section>

      <section className="login-card" aria-labelledby="login-title">
        <h2 id="login-title">Sign in</h2>
        <form onSubmit={handleSubmit}>
          <label>
            Email
            <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="username" required />
          </label>
          <label>
            Password
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required />
          </label>
          <button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Signing in…' : 'Sign in'}</button>
        </form>
        {error && <p className="status error" role="alert">{error}</p>}
        {user && <p className="status success">Verified by <code>/api/auth/me</code> as <strong>{displayNameFor(user)}</strong> for tenant <strong>{user.shaleClientId}</strong>. Token: <code>{tokenPreview}</code></p>}
      </section>
    </main>
  );
}
