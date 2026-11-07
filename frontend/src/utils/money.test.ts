import { describe, expect, it } from 'vitest';
import { illustratePlatformFee } from './money';

describe('illustratePlatformFee', () => {
  it('computes 5% of the original service fee, not of the total', () => {
    expect(illustratePlatformFee(100_000, 5)).toEqual({
      serviceFeePaise: 100_000,
      platformFeePaise: 5_000,
      totalPaise: 105_000,
    });
  });

  it('stays exact for fractional percentages', () => {
    expect(illustratePlatformFee(99_900, 5.5).platformFeePaise).toBe(5_495);
  });
});
