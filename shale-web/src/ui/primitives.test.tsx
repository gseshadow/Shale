import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Button, EntityCard, EntityList, Feedback, Field, NavigationButton, SectionRegion } from './primitives';
import { indicatorPaint } from './indicatorPaint';

afterEach(cleanup);
describe('shared presentation contracts', () => {
  it('keeps a stable polite atomic feedback region across confirmation updates', () => {
    const { rerender } = render(<Feedback aria-live="polite" aria-atomic="true">Pending</Feedback>);
    const status = screen.getByRole('status');
    rerender(<Feedback kind="unavailable" aria-live="polite" aria-atomic="true">Unconfirmed</Feedback>);
    expect(screen.getByRole('status')).toBe(status);
    expect(status.getAttribute('aria-live')).toBe('polite'); expect(status.getAttribute('aria-atomic')).toBe('true');
    rerender(<Feedback kind="error">Failure</Feedback>); expect(screen.getByRole('alert')).toBeTruthy();
  });
  it('keeps native action types, disabled behavior and navigation links', () => {
    const onClick = vi.fn();
    render(<MemoryRouter><Button purpose="primary">Check</Button><Button type="submit">Submit</Button>
      <Button purpose="danger" disabled onClick={onClick}>Delete unavailable</Button><NavigationButton to="/tasks">My Tasks</NavigationButton></MemoryRouter>);
    expect(screen.getByRole('button', { name: 'Check' }).getAttribute('type')).toBe('button');
    expect(screen.getByRole('button', { name: 'Submit' }).getAttribute('type')).toBe('submit');
    fireEvent.click(screen.getByRole('button', { name: 'Delete unavailable' })); expect(onClick).not.toHaveBeenCalled();
    expect(screen.getByRole('link', { name: 'My Tasks' }).getAttribute('href')).toBe('/tasks');
  });
  it('names card activation and selected state; isolates nested actions from card activation', () => {
    const open = vi.fn(); const complete = vi.fn();
    render(<EntityList ariaLabel="Examples"><EntityCard title="Long example" selected onClick={open} ariaLabel="Select long example"
      actions={<Button onClick={complete}>Complete task</Button>} metadata={<span>Card background</span>} /></EntityList>);
    const activation = screen.getByRole('button', { name: 'Select long example' });
    expect(activation.getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByText('✓ Selected')).toBeTruthy();
    fireEvent.click(activation); expect(open).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: 'Complete task' }));
    expect(complete).toHaveBeenCalledTimes(1); expect(open).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByText('Card background')); expect(open).toHaveBeenCalledTimes(2);
  });
  it('links validation to its labelled input and names section regions', () => {
    render(<SectionRegion title="Editor"><Field label="Example name" error="Enter a name" /></SectionRegion>);
    const input = screen.getByRole('textbox', { name: 'Example name' });
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(document.getElementById(input.getAttribute('aria-describedby')!)?.textContent).toBe('Enter a name');
    expect(screen.getByRole('region', { name: 'Editor' })).toBeTruthy();
  });
  it.each([null, '', 'invalid', 'url(example)', '#abc', '#123456789'])('uses a theme-neutral fallback for invalid colors (%s)', value => {
    expect(indicatorPaint(value)).toBeUndefined();
  });
  it.each(['#000000', '#ffffff', '#ff0000', '0x7B3FE480', '12345600', 'abcdef', '#176b42'])('provides at least 4.5:1 text contrast against the actual fill (%s)', value => {
    const paint = indicatorPaint(value)! as Record<string, string>;
    const rgb = paint['--shale-indicator-fill'].match(/\d+/g)!.map(Number);
    const linear = rgb.map(n => n / 255 <= .04045 ? n / 255 / 12.92 : ((n / 255 + .055) / 1.055) ** 2.4);
    const l = .2126 * linear[0] + .7152 * linear[1] + .0722 * linear[2];
    const contrast = paint['--shale-indicator-text'] === '#ffffff' ? 1.05 / (l + .05) : (l + .05) / .05;
    expect(contrast).toBeGreaterThanOrEqual(4.5);
  });
});
