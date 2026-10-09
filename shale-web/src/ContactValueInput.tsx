import { useEffect, useRef, useState } from 'react';
import type { InputHTMLAttributes } from 'react';
import { ApiError, validateContactValue } from './api';
import type { FieldError } from './api';
import { captureSessionRequestGuard } from './sessionRequests';

type Props = InputHTMLAttributes<HTMLInputElement> & {
  accessToken: string | null;
  kind: 'phone' | 'email';
  baseline?: string | null;
  extension?: string;
  baselineExtension?: string | null;
  onExtensionChange?: (value: string) => void;
  onFormatted?: (number: string, extension: string) => void;
  onValidated?: (field: string) => void;
  serverErrors?: FieldError[];
};

/** Uses the server's shared parser; typing never reformats or dismisses failed validation. */
export function ContactValueInput({ accessToken, kind, baseline, baselineExtension, extension,
  onExtensionChange, onFormatted, onValidated, serverErrors = [], ...props }: Props) {
  const [feedback, setFeedback] = useState('');
  const [invalidField, setInvalidField] = useState<'number' | 'extension' | null>(null);
  const generation = useRef(0);
  const retained = baseline !== undefined && props.value === (baseline ?? '')
    && (extension === undefined || extension === (baselineExtension ?? ''));
  const fieldName = props.id?.includes('fax') ? 'fax' : kind;
  const errors = serverErrors.filter(error => error.field === fieldName || error.field.startsWith(`${fieldName}.`));

  async function validate(format: boolean) {
    if (!accessToken) return;
    const current = ++generation.current;
    const sessionIsCurrent = captureSessionRequestGuard(accessToken);
    try {
      const result = await validateContactValue(accessToken, kind, String(props.value ?? ''), extension);
      if (current !== generation.current || !sessionIsCurrent()) return;
      setFeedback(result?.preview ?? '');
      setInvalidField(null);
      onValidated?.(fieldName);
      if (format && !retained && kind === 'phone' && result?.displayInput)
        onFormatted?.(result.displayInput, result.extension ?? '');
    } catch (error) {
      if (current !== generation.current || !sessionIsCurrent()) return;
      setInvalidField(!retained && error instanceof ApiError && error.fieldErrors.length > 0
        ? error.fieldErrors[0].field.endsWith('.extension') ? 'extension' : 'number' : null);
      setFeedback((retained ? 'Saved value needs review; you can retain it. ' : '')
        + (error instanceof Error ? error.message : 'Check this field.'));
    }
  }
  useEffect(() => {
    if (baseline !== undefined) void validate(false);
    return () => { generation.current++; };
  }, [accessToken]);
  return <><input {...props} data-validation-field={fieldName} type="text"
    onBlur={() => void validate(true)} onChange={event => { generation.current++; props.onChange?.(event); }}
    aria-invalid={invalidField === 'number' || errors.some(error => error.field === fieldName)} aria-describedby={`${props.id}-feedback`} />
    {kind === 'phone' && <><small>US full or 7-digit local numbers; use +country code for international numbers.</small>
      <label htmlFor={`${props.id}-extension`}>Extension (optional, 1–12 digits)</label>
      <input id={`${props.id}-extension`} type="text" inputMode="numeric" value={extension ?? ''}
        onChange={event => { generation.current++; onExtensionChange?.(event.target.value); }}
        onBlur={() => void validate(true)} disabled={props.disabled} data-validation-field={`${fieldName}.extension`}
        aria-invalid={invalidField === 'extension' || errors.some(error => error.field.endsWith('.extension'))}
        aria-describedby={`${props.id}-feedback`} /></>}
    <small id={`${props.id}-feedback`} role="status">{errors.length ? errors.map(error => error.message).join(' ') : feedback}</small></>;
}

/** A corrected field clears only its own server errors; unrelated failures stay visible. */
export function useContactFormErrors() {
  const [state, setState] = useState<{ message: string | null; errors: FieldError[] }>({ message: null, errors: [] });
  function setSubmitError(message: string | null) { setState({ message, errors: [] }); }
  function recordContactError(error: unknown, fallback: string) {
    setState({ errors: error instanceof ApiError ? error.fieldErrors : [], message: error instanceof Error ? error.message : fallback });
  }
  function clearContactError(field: string) {
    setState(current => {
      // Advisory syntax validation cannot dismiss authoritative uniqueness failures.
      const corrected = (error: FieldError) => error.code !== 'duplicate_phone'
        && (error.field === field || error.field.startsWith(`${field}.`));
      if (!current.errors.some(corrected)) return current;
      const errors = current.errors.filter(error => !corrected(error));
      return { errors, message: errors.length ? errors.map(error => error.message).join(' ') : null };
    });
  }
  return { submitError: state.message, setSubmitError, recordContactError, clearContactError, fieldErrors: state.errors };
}
