import { describe, expect, it } from 'vitest';
import { formatINR, formatIndianNumber, formatWallTime } from './format';

describe('formatINR', () => {
  it('uses the rupee symbol and Indian digit grouping', () => {
    expect(formatINR(100)).toBe('₹100');
    expect(formatINR(1000)).toBe('₹1,000');
    expect(formatINR(10000)).toBe('₹10,000');
    expect(formatINR(1250000)).toBe('₹12,50,000');
  });

  it('keeps paise when present and accepts decimal strings from the API', () => {
    expect(formatINR('1050.50')).toBe('₹1,050.5');
    expect(formatINR('52.25')).toBe('₹52.25');
  });

  it('renders a dash for invalid input instead of NaN', () => {
    expect(formatINR('abc')).toBe('—');
  });
});

describe('formatIndianNumber', () => {
  it('groups lakhs and crores', () => {
    expect(formatIndianNumber(12345678)).toBe('1,23,45,678');
  });
});

describe('formatWallTime', () => {
  it('formats wall-clock times without shifting time zones', () => {
    expect(formatWallTime('09:00:00')).toMatch(/^9:00\s?am$/i);
    expect(formatWallTime('18:30:00')).toMatch(/^6:30\s?pm$/i);
  });
});
