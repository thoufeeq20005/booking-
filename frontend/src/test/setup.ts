import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach } from 'vitest';
import { cleanup, configure } from '@testing-library/react';
import i18n from '@/i18n';
import { session } from '@/services/apiClient';
import { installFakeApi } from './fakeApi';

void i18n.changeLanguage('en');

// Route pages are lazy chunks; under a parallel run the first render can take longer than the 1 s default.
configure({ asyncUtilTimeout: 10000 });

if (!window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList;
}

// No test talks to a real server: unscripted requests get a 404 envelope (so session restore finds no session).
beforeEach(() => {
  installFakeApi(() => undefined);
});

afterEach(() => {
  cleanup();
  localStorage.clear();
  session.clear();
});
