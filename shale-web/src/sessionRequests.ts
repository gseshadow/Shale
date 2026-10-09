// One operational session owner. Preview does not import or activate this seam.
// A binding's identity is the generation witness, even if a later login reuses a token.
interface Binding {
  token: string;
  controller: AbortController;
  isCurrent: () => boolean;
  onRejected: () => void;
}
let active: Binding | null = null;

export class SessionRequestDiscarded extends Error {
  constructor() {
    super('This request belongs to a session that is no longer active. A submitted change may have been saved; check its outcome before submitting again.');
    this.name = 'SessionRequestDiscarded';
  }
}

export function bindSessionRequests(token: string, isCurrent: () => boolean, onRejected: () => void): () => void {
  active?.controller.abort();
  const binding: Binding = { token, isCurrent, onRejected, controller: new AbortController() };
  active = binding;
  return () => {
    if (active === binding) active = null;
    binding.controller.abort();
  };
}

// The feature clients only consume these response properties. Body settlement also
// checks the binding: receiving headers while current does not authorize a late body.
export type FeatureResponse = Pick<Response, 'ok' | 'status' | 'json'>;
export function captureSessionRequestGuard(token: string | null): () => boolean {
  const binding = active;
  return () => !!binding && active === binding && binding.token === token
    && !binding.controller.signal.aborted && binding.isCurrent();
}

export async function sessionFetch(token: string, input: string, init: RequestInit): Promise<FeatureResponse> {
  const binding = active;
  function assertCurrent() {
    if (!binding || active !== binding || binding.token !== token || !binding.isCurrent()
      || binding.controller.signal.aborted) throw new SessionRequestDiscarded();
  }
  assertCurrent();
  const signal = binding!.controller.signal;
  async function guarded<T>(operation: () => Promise<T>): Promise<T> {
    assertCurrent();
    let cancel!: () => void;
    const cancelled = new Promise<never>((_resolve, reject) => {
      cancel = () => reject(new SessionRequestDiscarded());
    });
    signal.addEventListener('abort', cancel, { once: true });
    try {
      const value = await Promise.race([operation(), cancelled]);
      assertCurrent();
      return value;
    } catch (error) {
      assertCurrent();
      throw error;
    } finally {
      signal.removeEventListener('abort', cancel);
    }
  }
  const response = await guarded(() => fetch(input, { ...init, signal }));
  assertCurrent(); // Another response may have consumed the binding in this microtask turn.
  // Inspected ApiReadController and ContactValueValidationController contracts:
  // only 401 establishes session rejection. 403 is operation-level authorization.
  // Login, /me, refresh and logout deliberately do not use this feature seam.
  if (response.status === 401) {
    active = null; // Consume synchronously, before notification or another settlement.
    binding!.controller.abort();
    binding!.onRejected();
    throw new SessionRequestDiscarded();
  }
  return { ok: response.ok, status: response.status, json: () => guarded(() => response.json()) };
}
