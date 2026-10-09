import { useEffect, useRef, useState } from 'react';
import { useBlocker } from 'react-router-dom';
import { Button } from './ui/primitives';
import './contactDraftProtection.css';

// Deliberately scoped to Contact Detail's existing information editor, not an all-form service.
export function useContactDraftProtection({ dirty, pending, isCurrent, onCancel }: {
  dirty: boolean; pending: boolean; isCurrent: () => boolean; onCancel: () => void;
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
      ? <ContactDiscardDialog pending={pending} onKeep={keepEditing} onDiscard={discard} /> : null,
  };
}

function ContactDiscardDialog({ pending, onKeep, onDiscard }: {
  pending: boolean; onKeep: () => void; onDiscard: () => void;
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
  return <dialog ref={dialog} className="contact-discard-dialog" aria-labelledby="contact-discard-title"
    aria-describedby="contact-discard-description" onCancel={event => { event.preventDefault(); onKeep(); }}
    onKeyDown={event => {
      if (event.key !== 'Tab') return;
      const buttons = event.currentTarget.querySelectorAll<HTMLButtonElement>('button');
      const first = buttons[0], last = buttons[buttons.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }}>
    <h2 id="contact-discard-title">Discard contact changes?</h2>
    <p id="contact-discard-description">Your unsaved contact changes will be discarded.
      {pending && ' A save is still pending and may already have committed. Leaving does not cancel or undo it. Check the contact before submitting again.'}</p>
    <div className="form-actions">
      <Button data-keep-editing purpose="secondary" onClick={onKeep}>Keep editing</Button>
      <Button purpose="danger" onClick={onDiscard}>Discard changes</Button>
    </div>
  </dialog>;
}
