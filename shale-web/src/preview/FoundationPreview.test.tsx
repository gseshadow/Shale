import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { FoundationPreview } from './FoundationPreview';
import { destinations } from '../shell/navigation';
import { foundationUrl } from './navigation';

beforeEach(() => {
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); });
function preview(path = '/my-shale') {
  return render(<MemoryRouter initialEntries={[foundationUrl(path)]}><FoundationPreview /></MemoryRouter>);
}
describe('isolated synthetic foundation', () => {
  it('does not call APIs or persist anything, even with a stored beta bearer', () => {
    sessionStorage.setItem('shale-web.accessToken', 'synthetic-test-token');
    const fetch = vi.fn(); vi.stubGlobal('fetch', fetch);
    const reads = vi.spyOn(Storage.prototype, 'getItem');
    const clears = vi.spyOn(Storage.prototype, 'removeItem');
    const writes = vi.spyOn(Storage.prototype, 'setItem');
    preview(); fireEvent.change(screen.getByLabelText('Preview theme'), { target: { value: 'dark' } });
    fireEvent.click(screen.getByRole('button', { name: 'Select example case' }));
    fireEvent.click(screen.getByRole('button', { name: 'Select example contact' }));
    expect(screen.getByRole('button', { name: 'Select example contact' }).getAttribute('aria-pressed')).toBe('true');
    fireEvent.click(screen.getByRole('button', { name: 'Check example' }));
    expect(fetch).not.toHaveBeenCalled(); expect(writes).not.toHaveBeenCalled(); expect(reads).not.toHaveBeenCalled(); expect(clears).not.toHaveBeenCalled();
    sessionStorage.clear();
  });
  it.each(destinations)('keeps $label reachable and exposes the active destination without color', async destination => {
    preview();
    const details = document.querySelector('details')!; details.open = true;
    const link = screen.getByRole('link', { name: destination.available ? destination.label : `${destination.label} Unavailable` });
    fireEvent.click(link);
    await waitFor(() => expect(screen.getByRole('heading', { level: 1 }).textContent).toBe(destination.label));
    expect(link.getAttribute('aria-current')).toBe('page');
    if (!destination.available) expect(screen.getByText(`${destination.label} is unavailable in the web application. This preview adds no functionality for this destination.`)).toBeTruthy();
  });
  it('closes compact navigation with Escape and returns focus; navigation focuses content', async () => {
    preview(); const details = document.querySelector('details')!; const summary = document.querySelector('summary')!;
    details.open = true;
    const cases = screen.getByRole('link', { name: 'Cases' }); cases.focus(); fireEvent.keyDown(cases, { key: 'Escape' });
    expect(details.open).toBe(false); expect(document.activeElement).toBe(summary);
    details.open = true; fireEvent.click(cases);
    await waitFor(() => expect(document.activeElement).toBe(screen.getByRole('main')));
    expect(details.open).toBe(false);
  });
  it('focuses a local validation error and never claims persistence success', () => {
    preview(); fireEvent.click(screen.getByRole('button', { name: 'Check example' }));
    const field = screen.getByRole('textbox', { name: 'Example name' });
    expect(document.activeElement).toBe(field); expect(field.getAttribute('aria-invalid')).toBe('true');
    fireEvent.change(field, { target: { value: 'Synthetic example' } }); fireEvent.click(screen.getByRole('button', { name: 'Check example' }));
    expect(screen.getByText('Example validation passed. Nothing was saved or sent.')).toBeTruthy();
  });
  it('uses the expanded shared navigation at medium and wide sizes', () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
    preview(); expect(document.querySelector('details')!.open).toBe(true);
    expect(screen.getAllByRole('link').filter(link => link.getAttribute('aria-current') === 'page')).toHaveLength(1);
  });
});
