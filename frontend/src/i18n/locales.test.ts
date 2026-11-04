import { describe, expect, it } from 'vitest';
import en from './locales/en.json';
import hi from './locales/hi.json';
import kn from './locales/kn.json';
import ml from './locales/ml.json';
import ta from './locales/ta.json';
import te from './locales/te.json';

type Tree = { [key: string]: string | Tree };

function flatten(tree: Tree, prefix = ''): Record<string, string> {
  return Object.entries(tree).reduce<Record<string, string>>((acc, [k, v]) => {
    const key = prefix ? `${prefix}.${k}` : k;
    if (typeof v === 'string') acc[key] = v;
    else Object.assign(acc, flatten(v, key));
    return acc;
  }, {});
}

const placeholders = (s: string) => (s.match(/{{\s*\w+\s*}}/g) ?? []).sort();

const reference = flatten(en);
const locales = { ta, hi, ml, te, kn } as Record<string, Tree>;

describe.each(Object.entries(locales))('%s translations', (_code, messages) => {
  const flat = flatten(messages);

  it('has exactly the same keys as English', () => {
    expect(Object.keys(flat).sort()).toEqual(Object.keys(reference).sort());
  });

  it('keeps every interpolation placeholder', () => {
    for (const [key, value] of Object.entries(reference)) {
      expect(placeholders(flat[key] ?? ''), key).toEqual(placeholders(value));
    }
  });

  it('has no empty strings', () => {
    expect(Object.entries(flat).filter(([, v]) => !v.trim())).toEqual([]);
  });
});
