import { useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ContactValueInput, useContactFormErrors } from './ContactValueInput';
import { ApiError, validateContactValue } from './api';
import { bindSessionRequests } from './sessionRequests';

vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof import('./api')>(), validateContactValue: vi.fn() }));
const validate = vi.mocked(validateContactValue);
const phoneError = { field: 'phone', code: 'invalid_phone', message: 'Phone is invalid.' };
const emailError = { field: 'email', code: 'invalid_email', message: 'Email is invalid.' };
function Form({ baseline, fax = false }: { baseline?: string; fax?: boolean }) {
  const [number, setNumber] = useState(baseline ?? '');
  const [extension, setExtension] = useState('');
  const errors = useContactFormErrors();
  return <><ContactValueInput id={fax ? 'organization-fax' : 'contact-phone'} aria-label="Number" kind="phone"
    accessToken="test-token" value={number} baseline={baseline} extension={extension}
    onChange={event => setNumber(event.target.value)} onExtensionChange={setExtension}
    onFormatted={(value, ext) => { setNumber(value); setExtension(ext); }}
    onValidated={errors.clearContactError} serverErrors={errors.fieldErrors} />
    <button onClick={() => errors.recordContactError(new ApiError('Phone is invalid. Email is invalid.', 400,
      [{ ...phoneError, field: fax ? 'fax' : 'phone' }, emailError]), 'Save failed.')}>Save failure</button>
    <button onClick={() => errors.recordContactError(new ApiError('Duplicate phone.', 400,
      [{ field: 'phone', code: 'duplicate_phone', message: 'Duplicate phone.' }]), 'Save failed.')}>Duplicate failure</button>
    <p data-testid="summary">{errors.submitError}</p></>;
}
function number() { return screen.getByLabelText<HTMLInputElement>('Number'); }
function extension() { return screen.getByLabelText<HTMLInputElement>('Extension (optional, 1–12 digits)'); }
let disposeSession: () => void;
beforeEach(() => { validate.mockReset(); disposeSession = bindSessionRequests('test-token', () => true, () => {}); });
afterEach(() => { cleanup(); disposeSession(); });

describe('phone entry in browser Contact and Organization forms', () => {
  it('disabling for Save invalidates earlier advisory callbacks even after re-enabling', async () => {
    let resolve!: (value: { displayInput: string }) => void;
    validate.mockImplementation(() => new Promise(done => { resolve = done; }));
    const formatted = vi.fn();
    const input = (disabled: boolean) => <ContactValueInput id="pending-phone" aria-label="Number" kind="phone"
      accessToken="test-token" value="5551234" disabled={disabled} onFormatted={formatted} />;
    const view = render(input(false)); fireEvent.blur(number());
    view.rerender(input(true)); view.rerender(input(false));
    await act(async () => resolve({ displayInput: '555-1234' }));
    expect(formatted).not.toHaveBeenCalled(); expect(number().value).toBe('5551234');
  });
  it('does not format or invoke parent callbacks after session teardown before unmount', async () => {
    let resolve!: (value: { displayInput: string }) => void;
    validate.mockImplementation(() => new Promise(done => { resolve = done; }));
    render(<Form />); fireEvent.change(number(), { target: { value: '5059033568' } }); fireEvent.blur(number());
    disposeSession(); await act(async () => { resolve({ displayInput: '(505) 903-3568' }); });
    expect(number().value).toBe('5059033568');
    expect(screen.getByRole('status').textContent).toBe('');
  });
  it('keeps pasted punctuation/text intact until shared validation formats on blur', async () => {
    validate.mockResolvedValue({ displayInput: '(505) 903-3568', extension: '001', preview: '(505) 903-3568 ext. 001' });
    render(<Form />);
    fireEvent.change(number(), { target: { value: 'Call: (505) 903 3568 x001' } });
    expect(number().value).toBe('Call: (505) 903 3568 x001');
    expect(validate).not.toHaveBeenCalled();
    fireEvent.blur(number());
    await waitFor(() => expect(number().value).toBe('(505) 903-3568'));
    expect(extension().value).toBe('001');
    expect(validate).toHaveBeenCalledWith('test-token', 'phone', 'Call: (505) 903 3568 x001', '');
  });
  it.each([false, true])('clears corrected phone/fax server errors while retaining other errors (fax=%s)', async fax => {
    validate.mockResolvedValue({ displayInput: '903-3568', extension: null, preview: '903-3568 · US local; area code required to call' });
    render(<Form fax={fax} />);fireEvent.click(screen.getByText('Save failure'));
    expect(number().getAttribute('aria-invalid')).toBe('true');
    fireEvent.change(number(), { target: { value: '(903) 3568' } });
    expect(screen.getByTestId('summary').textContent).toBe('Phone is invalid. Email is invalid.');
    fireEvent.blur(number());
    await waitFor(() => expect(screen.getByTestId('summary').textContent).toBe('Email is invalid.'));
    expect(number().getAttribute('aria-invalid')).toBe('false');expect(number().value).toBe('903-3568');
  });
  it('keeps invalid drafts and their errors until corrected input validates', async () => {
    validate.mockRejectedValue(new ApiError('Phone is invalid.', 400, [phoneError]));
    render(<Form />);fireEvent.change(number(), { target: { value: '0 --' } });fireEvent.blur(number());
    await waitFor(() => expect(number().getAttribute('aria-invalid')).toBe('true'));
    expect(number().value).toBe('0 --');
    fireEvent.change(number(), { target: { value: '1 505 903 3568' } });
    expect(screen.getByRole('status').textContent).toBe('Phone is invalid.');
    validate.mockResolvedValue({ displayInput: '+1 (505) 903-3568', preview: '+1 (505) 903-3568' });
    fireEvent.blur(number());await waitFor(() => expect(number().getAttribute('aria-invalid')).toBe('false'));
    expect(number().value).toBe('+1 (505) 903-3568');
  });
  it.each(['0', '3035550123'])('retains unchanged legacy spelling on load and blur (%s)', async baseline => {
    validate.mockResolvedValue({ displayInput: '(303) 555-0123', preview: '(303) 555-0123' });
    render(<Form baseline={baseline} />);await waitFor(() => expect(validate).toHaveBeenCalledTimes(1));
    fireEvent.blur(number());await waitFor(() => expect(validate).toHaveBeenCalledTimes(2));
    expect(number().value).toBe(baseline);
  });
  it('retains authoritative duplicate errors until a Save can verify uniqueness', async () => {
    validate.mockResolvedValue({ displayInput: '(505) 903-3568', preview: '(505) 903-3568' });
    render(<Form />);fireEvent.click(screen.getByText('Duplicate failure'));
    fireEvent.change(number(), { target: { value: '5059033568' } });fireEvent.blur(number());
    await waitFor(() => expect(number().value).toBe('(505) 903-3568'));
    expect(screen.getByTestId('summary').textContent).toBe('Duplicate phone.');
    expect(number().getAttribute('aria-invalid')).toBe('true');
  });
  it('ignores an old blur response after more typing', async () => {
    let resolve!: (value: { displayInput: string }) => void;
    validate.mockImplementation(() => new Promise(done => { resolve = done; }));
    render(<Form />);fireEvent.change(number(), { target: { value: '5059033568' } });fireEvent.blur(number());
    fireEvent.change(number(), { target: { value: '9033568' } });resolve({ displayInput: '(505) 903-3568' });
    await waitFor(() => expect(number().value).toBe('9033568'));
  });
});
