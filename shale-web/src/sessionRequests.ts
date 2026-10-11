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
export type FeatureResponse = Pick<Response, 'ok' | 'status' | 'json'> & { boundedText: (maximumBytes: number) => Promise<string>; dispose: () => void };
export class FeatureRequestCancelled extends Error {}
export class FeaturePayloadOversized extends Error {}
export class FeaturePayloadMalformed extends Error {}
export function captureSessionRequestGuard(token: string | null): () => boolean {
  const binding = active;
  return () => !!binding && active === binding && binding.token === token
    && !binding.controller.signal.aborted && binding.isCurrent();
}

export async function sessionFetch(token: string, input: string, init: RequestInit): Promise<FeatureResponse> {
  const binding = active;
  const caller = init.signal;
  const transport = new AbortController();
  function assertCurrent() {
    if (!binding || active !== binding || binding.token !== token || !binding.isCurrent()
      || binding.controller.signal.aborted) throw new SessionRequestDiscarded();
    if (caller?.aborted) throw new FeatureRequestCancelled();
  }
  assertCurrent();
  const signal = binding!.controller.signal;
  // Legacy exchanges retain the original lifetime signal. A caller-aware exchange
  // keeps forwarding attached across headers AND body, including the gap between them.
  const abortTransport = () => transport.abort();
  const disposeTransport = () => {
    signal.removeEventListener('abort', abortTransport);
    caller?.removeEventListener('abort', abortTransport);
  };
  if (caller) {
    signal.addEventListener('abort', abortTransport, { once: true });
    caller.addEventListener('abort', abortTransport, { once: true });
  }
  async function guarded<T>(operation: () => Promise<T>): Promise<T> {
    assertCurrent();
    let cancel!: () => void;
    const cancelled = new Promise<never>((_resolve, reject) => {
      cancel = () => { transport.abort(); reject(new SessionRequestDiscarded()); };
    });
    const cancelCaller = () => { transport.abort(); rejectCaller(); };
    let rejectCaller!: () => void;
    const callerCancelled = new Promise<never>((_resolve, reject) => { rejectCaller = () => reject(new FeatureRequestCancelled()); });
    signal.addEventListener('abort', cancel, { once: true });
    caller?.addEventListener('abort', cancelCaller, { once: true });
    try {
      const value = await Promise.race([operation(), cancelled, callerCancelled]);
      assertCurrent();
      return value;
    } catch (error) {
      assertCurrent();
      throw error;
    } finally {
      signal.removeEventListener('abort', cancel);
      caller?.removeEventListener('abort', cancelCaller);
    }
  }
  let response: Response;
  try { response = await guarded(() => fetch(input, { ...init, signal: caller ? transport.signal : signal })); }
  catch (error) { disposeTransport(); throw error; }
  try { assertCurrent(); } catch (error) { disposeTransport(); throw error; } // Another response may have consumed the binding in this microtask turn.
  // Inspected ApiReadController and ContactValueValidationController contracts:
  // only 401 establishes session rejection. 403 is operation-level authorization.
  // Login, /me, refresh and logout deliberately do not use this feature seam.
  if (response.status === 401) {
    transport.abort(); // Stop any rejected response body before detaching caller forwarding.
    disposeTransport();
    active = null; // Consume synchronously, before notification or another settlement.
    binding!.controller.abort();
    binding!.onRejected();
    throw new SessionRequestDiscarded();
  }
  return { ok: response.ok, status: response.status, json: () => guarded(() => response.json()).finally(disposeTransport), dispose: disposeTransport,
    boundedText: maximumBytes => guarded(async () => {
      // Count actual streamed UTF-8 bytes, including whitespace/escapes; never trust Content-Length.
      if (!response.body) throw new FeaturePayloadMalformed();
      const reader = response.body.getReader();
      const cancelReader = () => { void reader.cancel().catch(() => {}); };
      transport.signal.addEventListener('abort', cancelReader, { once: true });
      const decoder = new TextDecoder('utf-8', { fatal: true });
      let bytes = 0, text = '';
      try {
        while (true) {
          const chunk = await reader.read();
          assertCurrent();
          if (chunk.done) break;
          bytes += chunk.value.byteLength;
          if (bytes > maximumBytes) throw new FeaturePayloadOversized();
          try { text += decoder.decode(chunk.value, { stream: true }); } catch { throw new FeaturePayloadMalformed(); }
        }
        try { return text + decoder.decode(); } catch { throw new FeaturePayloadMalformed(); }
      } catch (error) {
        // Cancellation is best effort; it does not undo a server read or committed audit.
        void reader.cancel().catch(() => {});
        assertCurrent();
        throw error;
      } finally { transport.signal.removeEventListener('abort', cancelReader); reader.releaseLock(); disposeTransport(); }
    }).finally(disposeTransport),
  };
}

// Bounded feature memory may subscribe to the same synchronous teardown witness.
// This does not own rejection, credentials, or the session generation.
export function observeSessionRequestEnd(token: string, clear: () => void): () => void {
  const binding = active;
  if (!binding || binding.token !== token || binding.controller.signal.aborted) { clear(); return () => {}; }
  binding.controller.signal.addEventListener('abort', clear, { once: true });
  return () => binding.controller.signal.removeEventListener('abort', clear);
}
