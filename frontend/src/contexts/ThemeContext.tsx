import { createContext, useContext, useEffect, type ReactNode } from 'react';

/** BookEasy has one clean white theme. The context stays so components can ask which theme is active. */
export type Theme = 'light';

interface ThemeContextValue {
  theme: Theme;
}

const VALUE: ThemeContextValue = { theme: 'light' };
const ThemeContext = createContext<ThemeContextValue>(VALUE);

export function ThemeProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    document.documentElement.dataset.theme = 'light';
  }, []);
  return <ThemeContext.Provider value={VALUE}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  return useContext(ThemeContext);
}
