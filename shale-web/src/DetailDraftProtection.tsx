import { useEffect, useRef, useState } from 'react';
import { useBlocker } from 'react-router-dom';
import { Button } from './ui/primitives';
import './detailDraftProtection.css';

// Shared only by the Contact and Organization Detail information editors. Each owns its baseline.
export function useDetailDraftProtection({ entity, dirty, pending, isCurrent, onCancel }: {
  entity: 'contact' | 'organization'; dirty: boolean; pending: boolean; isCurrent: () => boolean; onCancel: () => void;
}) {
  const discarded = useRef(false);
  const [cancelRequested, setCancelRequested] = useState(false);
  const blocker = useBlocker(() => !discarded.current && isCurrent() && (dirty || pending));
  const current = useRef({ blocker, isCurrent });
  current.current = { blocker, isCurrent };
  const needed = dirty || pending;
  useEffect(() => {
    // Advisory formatting can return the last edited value to the opening baseline
    // while a prompt is open. Release that prompt without replaying navigation.
    if (!needed) {
      if (blocker.state === 'blocked') blocker.reset();
      setCancelRequested(false);
    }
  }, [needed, blocker]);
  useEffect(() => {
    if (!needed) return;
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (discarded.current || !current.current.isCurrent()) return;
      event.preventDefault(); event.returnValue = '';
    };
    window.addEventListener('beforeunload', beforeUnload);
    return () => window.removeEventListener('beforeunload', beforeUnload);
  }, [needed]);

  function keepEditing() {
    if (blocker.state === 'blocked') blocker.reset();
    setCancelRequested(false);
  }
  function discard() {
    if (discarded.current || !isCurrent()) return;
    discarded.current = true; // Synchronous guard: permit the router's intended transition once.
    setCancelRequested(false);
    if (blocker.state === 'blocked') blocker.proceed();
    else onCancel();
  }
  function saved() {
    // Known success closes this editor. A navigation attempted during Save is not auto-replayed.
    discarded.current = true;
    const active = current.current.blocker;
    if (active.state === 'blocked') active.reset();
    setCancelRequested(false);
  }
  return {
    saved,
    isDiscarded: () => discarded.current,
    cancel: () => { if (needed) setCancelRequested(true); else onCancel(); },
    confirmation: (blocker.state === 'blocked' || cancelRequested)
      ? <DetailDiscardDialog entity={entity} pending={pending} onKeep={keepEditing} onDiscard={discard} /> : null,
  };
}

function DetailDiscardDialog({ entity, pending, onKeep, onDiscard }: {
  entity: 'contact' | 'organization'; pending: boolean; onKeep: () => void; onDiscard: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const previous = document.activeElement;
    const node = dialog.current!;
    node.showModal();
    node.querySelector<HTMLButtonElement>('[data-keep-editing]')?.focus();
    return () => {
      node.close();
      if (previous instanceof HTMLElement && previous.isConnected) previous.focus();
    };
  }, []);
  return <dialog ref={dialog} className="detail-discard-dialog" aria-labelledby="detail-discard-title"
    aria-describedby="detail-discard-description" onCancel={event => { event.preventDefault(); onKeep(); }}
    onKeyDown={event => {
      if (event.key !== 'Tab') return;
      const buttons = event.currentTarget.querySelectorAll<HTMLButtonElement>('button');
      const first = buttons[0], last = buttons[buttons.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }}>
    <h2 id="detail-discard-title">Discard {entity} changes?</h2>
    <p id="detail-discard-description">Your unsaved {entity} changes will be discarded.
      {pending && ' A save is still pending and may already have committed. Leaving does not cancel or undo it. Check the ' + entity + ' before submitting again.'}</p>
    <div className="form-actions">
      <Button data-keep-editing purpose="secondary" onClick={onKeep}>Keep editing</Button>
      <Button purpose="danger" onClick={onDiscard}>Discard changes</Button>
    </div>
  </dialog>;
}
