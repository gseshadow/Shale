import { useId } from 'react';
import type { ButtonHTMLAttributes, HTMLAttributes, InputHTMLAttributes, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { indicatorPaint } from './indicatorPaint';

export type ActionRole = 'primary' | 'secondary' | 'ghost' | 'danger' | 'navigation';
export function Button({ purpose = 'secondary', size = 'standard', className = '', type = 'button', ...props }:
  ButtonHTMLAttributes<HTMLButtonElement> & { purpose?: ActionRole; size?: 'standard' | 'small' }) {
  return <button {...props} type={type} className={`shale-button shale-button-${purpose} shale-button-${size} ${className}`} />;
}
export function NavigationButton({ to, children }: { to: string; children: ReactNode }) {
  return <Link className="shale-button shale-button-navigation shale-button-standard" to={to}>{children}</Link>;
}
// Compatibility adapters keep the beta's current CSS and all handlers until deliberate adoption.
export function ActionButton(props: ButtonHTMLAttributes<HTMLButtonElement>) {
  return <Button {...props} purpose="primary" className={`action-button ${props.className ?? ''}`} />;
}
export function SecondaryButton(props: ButtonHTMLAttributes<HTMLButtonElement>) {
  return <Button {...props} purpose="secondary" className={`secondary-button ${props.className ?? ''}`} />;
}
export function ToolbarActions({ children }: { children: ReactNode }) {
  return <div className="toolbar-actions">{children}</div>;
}
export function PageHeader({ eyebrow, title, titleId, lede, action }: { eyebrow: string; title: string; titleId?: string; lede?: string; action?: ReactNode }) {
  return <header className="page-header"><div className="page-heading-row"><div className="page-title-block">
    <p className="eyebrow">{eyebrow}</p><h1 id={titleId}>{title}</h1>{lede && <p className="lede">{lede}</p>}
  </div>{action && <ToolbarActions>{action}</ToolbarActions>}</div></header>;
}
export function SectionRegion({ title, children, density = 'comfortable' }: { title: string; children: ReactNode; density?: 'comfortable' | 'compact' | 'dense' }) {
  const id = useId();
  return <section className={`shale-region shale-density-${density}`} aria-labelledby={id}>
    <h2 id={id}>{title}</h2>{children}</section>;
}
export function Feedback({ kind = 'info', children, ...props }: { kind?: 'info' | 'success' | 'error' | 'loading' | 'empty' | 'unavailable'; children: ReactNode } & Omit<HTMLAttributes<HTMLParagraphElement>, 'role' | 'className'>) {
  return <p {...props} className={`shale-feedback shale-feedback-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>{children}</p>;
}
export function LoadingState({ message }: { message: string }) {
  return <p className="status loading-state" role="status">{message}</p>;
}
export function EmptyState({ message }: { message: string }) {
  return <p className="status empty-state">{message}</p>;
}
export function StatusPill({ children, tone = 'neutral', color, variant = 'pill' }: {
  children: ReactNode; tone?: 'neutral' | 'success' | 'warning' | 'info'; color?: string | null; variant?: 'pill' | 'badge';
}) {
  const paint = indicatorPaint(color);
  return <span className={`status-pill status-pill-${tone} shale-indicator-${variant}`} style={paint}>
    {variant === 'badge' && <span className="shale-indicator-dot" aria-hidden="true" />}{children}</span>;
}
export function MetadataRow({ label, value }: { label: string; value: ReactNode }) {
  return <div className="metadata-row"><dt>{label}</dt><dd>{value}</dd></div>;
}
export function MetadataGrid({ children }: { children: ReactNode }) {
  return <dl className="metadata-grid">{children}</dl>;
}
export function EntityList({ children, ariaLabel }: { children: ReactNode; ariaLabel: string }) {
  return <div className="entity-list" role="list" aria-label={ariaLabel}>{children}</div>;
}
export function EntityCard({ title, subtitle, eyebrow, badges, metadata, actions, onClick, ariaLabel, selected, compact = false }: {
  title: ReactNode; subtitle?: ReactNode; eyebrow?: ReactNode; badges?: ReactNode; metadata?: ReactNode;
  actions?: ReactNode; onClick?: () => void; ariaLabel?: string; selected?: boolean; compact?: boolean;
}) {
  return <article role="listitem" className={`entity-card${onClick ? ' entity-card-clickable' : ''}${selected ? ' shale-card-selected' : ''}${compact ? ' shale-card-compact' : ''}`}
    aria-label={onClick ? undefined : ariaLabel}
    onClick={event => {
      if (!(event.target as HTMLElement).closest('button, a, input, select, textarea, label')) onClick?.();
    }}>
    <div className="entity-card-header"><div className="entity-card-title-block">
      {eyebrow && <p className="entity-card-eyebrow">{eyebrow}</p>}
      <h3 className="entity-card-title">{onClick
        ? <button type="button" className="entity-card-activation" aria-label={ariaLabel} aria-pressed={selected}
            onClick={onClick}>{title}{selected && <span className="shale-selected-marker" aria-hidden="true">✓ Selected</span>}</button>
        : title}</h3>
      {subtitle && <p className="entity-card-subtitle">{subtitle}</p>}
    </div>{badges && <div className="entity-card-badges">{badges}</div>}</div>
    {metadata && <div className="entity-card-metadata">{metadata}</div>}
    {actions && <div className="entity-card-actions" onClick={event => event.stopPropagation()} onKeyDown={event => event.stopPropagation()}>{actions}</div>}
  </article>;
}
export function Field({ label, error, hint, id, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string }) {
  const generated = useId();
  const fieldId = id ?? generated;
  const feedbackId = `${fieldId}-feedback`;
  return <div className="shale-field"><label htmlFor={fieldId}>{label}</label>
    <input {...props} id={fieldId} aria-invalid={error ? true : undefined}
      aria-describedby={[props['aria-describedby'], (error || hint) ? feedbackId : undefined].filter(Boolean).join(' ') || undefined} />
    {(error || hint) && <small id={feedbackId} className={error ? 'shale-field-error' : ''} role={error ? 'alert' : undefined}>{error ?? hint}</small>}
  </div>;
}
