import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ResponsiveShell } from './ResponsiveShell';

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
function media(matches: boolean) {
  let change!: () => void;
  const query = { matches, addEventListener: vi.fn((_event, handler) => { change = handler; }), removeEventListener: vi.fn() };
  vi.stubGlobal('matchMedia', vi.fn(() => query));
  return (wide: boolean) => act(() => { query.matches = wide; change(); });
}
function shell(key = 'first') {
  return <MemoryRouter><ResponsiveShell path="/cases" navigationKey={key} identity="Synthetic identity">
    <input aria-label="Route draft" defaultValue="Unsaved example" />
  </ResponsiveShell></MemoryRouter>;
}
describe('responsive shell focus lifecycle', () => {
  it('returns focus from navigation before compact disclosure hides its links', () => {
    const resize = media(true); render(shell());
    screen.getByRole('link', { name: 'Cases' }).focus(); resize(false);
    expect(document.querySelector('details')!.open).toBe(false);
    expect(document.activeElement).toBe(document.querySelector('summary'));
    resize(true); expect(document.querySelector('details')!.open).toBe(true);
  });
  it('preserves focused draft input across viewport changes', () => {
    const resize = media(true); render(shell());
    const field = screen.getByRole('textbox', { name: 'Route draft' }); field.focus(); resize(false); resize(true);
    expect(document.activeElement).toBe(field); expect((field as HTMLInputElement).value).toBe('Unsaved example');
  });
  it('focuses content and closes compact navigation for a new history entry at the same path', () => {
    media(false); const view = render(shell());
    document.querySelector('details')!.open = true;
    screen.getByRole('link', { name: 'Cases' }).focus(); view.rerender(shell('second'));
    expect(document.querySelector('details')!.open).toBe(false);
    expect(document.activeElement).toBe(screen.getByRole('main'));
  });
});
