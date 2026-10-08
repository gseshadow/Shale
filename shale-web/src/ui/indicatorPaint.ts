import type { CSSProperties } from 'react';

/** Accept desktop #RRGGBB / RRGGBBAA / 0xRRGGBBAA storage, never arbitrary CSS. */
export function indicatorPaint(stored?: string | null): CSSProperties | undefined {
  const hex = stored?.trim().replace(/^(#|0x)/i, '');
  if (!hex || !/^(?:[a-f\d]{6}|[a-f\d]{8})$/i.test(hex)) return undefined;
  const rgb = [0, 2, 4].map(i => Number.parseInt(hex.slice(i, i + 2), 16));
  const alpha = hex.length === 8 ? Number.parseInt(hex.slice(6), 16) / 255 : 1;
  // Resolve stored transparency onto a fixed white backing, matching the actual opaque fill.
  // This preserves its visual color and makes foreground contrast independent of theme/surface.
  const composite = rgb.map(channel => Math.round(channel * alpha + 255 * (1 - alpha)));
  const linear = composite.map(channel => {
    const value = channel / 255;
    return value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4;
  });
  const luminance = .2126 * linear[0] + .7152 * linear[1] + .0722 * linear[2];
  const foreground = (luminance + .05) / .05 >= 1.05 / (luminance + .05) ? '#000000' : '#ffffff';
  return { '--shale-indicator-fill': `rgb(${composite.join(', ')})`, '--shale-indicator-text': foreground } as CSSProperties;
}
