export class SessionAttemptCancelled extends Error {}
export class SessionAttemptTimedOut extends Error {}

// One budget for the entire parsed exchange. Cancellation races ignored-abort
// promises; stage/installation checks also reject elapsed time before timer delivery.
export async function withSessionDeadline<T>(controller: AbortController, budget: number,
  operation: (assertActive: () => void) => Promise<T>): Promise<T> {
  const deadline = performance.now() + budget;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let rejectCancellation!: (error: Error) => void;
  let timedOut = false;
  const cancelled = new Promise<never>((_resolve, reject) => { rejectCancellation = reject; });
  function cancel() {
    clearTimeout(timer);
    rejectCancellation(timedOut ? new SessionAttemptTimedOut() : new SessionAttemptCancelled());
  }
  function assertActive() {
    if (performance.now() >= deadline) {
      timedOut = true;
      controller.abort();
      throw new SessionAttemptTimedOut();
    }
    if (controller.signal.aborted) throw new SessionAttemptCancelled();
  }
  controller.signal.addEventListener('abort', cancel, { once: true });
  timer = setTimeout(() => { timedOut = true; controller.abort(); }, budget);
  try {
    if (controller.signal.aborted) cancel();
    else {
      const result = await Promise.race([operation(assertActive), cancelled]);
      assertActive();
      return result;
    }
    return await cancelled;
  } catch (error) {
    assertActive();
    throw error;
  } finally {
    clearTimeout(timer);
    controller.signal.removeEventListener('abort', cancel);
  }
}
