import { useSyncExternalStore } from 'react';
import type { QuickItem } from '@/services/discoveryApi';
import { storage } from '@/utils/storage';

/**
 * Recent searches and recently opened places, kept in this browser only (nothing is sent to the server except, as
 * hints, with a recommendations request). Shared by the search window, the home page and "Picked for you".
 */
const KEY = 'bookeasy.recent';
const MAX_TERMS = 8;
const MAX_VIEWED = 6;

export interface RecentState {
  terms: string[];
  viewed: QuickItem[];
}

const EMPTY: RecentState = { terms: [], viewed: [] };
let cache: RecentState | null = null;
const listeners = new Set<() => void>();

function read(): RecentState {
  if (cache) return cache;
  try {
    const raw = storage.get(KEY);
    const parsed = raw ? (JSON.parse(raw) as Partial<RecentState>) : {};
    cache = {
      terms: Array.isArray(parsed.terms) ? parsed.terms.filter((t) => typeof t === 'string').slice(0, MAX_TERMS) : [],
      viewed: Array.isArray(parsed.viewed) ? parsed.viewed.filter((v) => v && typeof v.href === 'string').slice(0, MAX_VIEWED) : [],
    };
  } catch {
    cache = EMPTY;
  }
  return cache;
}

function write(next: RecentState) {
  cache = next;
  storage.set(KEY, JSON.stringify(next));
  listeners.forEach((l) => l());
}

export const recent = {
  /** Remembers a search (newest first, no duplicates ignoring case). */
  addTerm(term: string) {
    const t = term.trim().replace(/\s+/g, ' ').slice(0, 60);
    if (t.length < 2) return;
    const s = read();
    write({ ...s, terms: [t, ...s.terms.filter((x) => x.toLowerCase() !== t.toLowerCase())].slice(0, MAX_TERMS) });
  },
  /** Remembers a place, movie or event the user opened from search. */
  addViewed(item: QuickItem) {
    const s = read();
    write({ ...s, viewed: [item, ...s.viewed.filter((x) => x.href !== item.href)].slice(0, MAX_VIEWED) });
  },
  removeTerm(term: string) {
    const s = read();
    write({ ...s, terms: s.terms.filter((x) => x !== term) });
  },
  clear() {
    write(EMPTY);
  },
  /** For tests: forget the in-memory copy so the next read comes from storage. */
  reset() {
    cache = null;
  },
};

function subscribe(l: () => void) {
  listeners.add(l);
  return () => {
    listeners.delete(l);
  };
}

export function useRecent(): RecentState {
  return useSyncExternalStore(subscribe, read, () => EMPTY);
}

/**
 * What the user seems interested in, for recommendations: their recent searches, then the kinds of places they
 * opened (e.g. "Gym", "Café"). At most five.
 */
export function interestsOf(s: RecentState): string[] {
  const out: string[] = [];
  const add = (v?: string | null) => {
    const t = v?.replace(/,/g, ' ').trim();
    if (t && t.length >= 2 && !out.some((x) => x.toLowerCase() === t.toLowerCase())) out.push(t);
  };
  s.terms.forEach(add);
  s.viewed.forEach((v) => add(v.category?.en));
  return out.slice(0, 5);
}
