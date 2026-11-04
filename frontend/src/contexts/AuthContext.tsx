import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import i18n, { LANGUAGES } from '@/i18n';
import { refreshSession, session, type Role, type Session, type UserView } from '@/services/apiClient';
import { accountApi, authApi } from '@/services/authApi';

type Status = 'loading' | 'authenticated' | 'anonymous';

interface AuthContextValue {
  status: Status;
  user: UserView | null;
  /** Call with any AUTHENTICATED session returned by the API. */
  signIn: (s: Session) => void;
  signOut: () => Promise<void>;
  /** Replace the cached user after an account change. */
  setUser: (u: UserView) => void;
  hasRole: (role: Role) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const isSupported = (lng: string) => LANGUAGES.some((l) => l.code === lng);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');
  const [user, setUserState] = useState<UserView | null>(null);

  useEffect(() => {
    const unsubscribe = session.subscribe((u) => {
      setUserState(u);
      setStatus(u ? 'authenticated' : 'anonymous');
    });
    // Restore the session from the refresh cookie on page load.
    void refreshSession().then((ok) => {
      if (!ok) setStatus('anonymous');
    });
    return () => {
      unsubscribe();
    };
  }, []);

  const signIn = useCallback((s: Session) => {
    session.apply(s);
    // Signing in adopts the language saved on the account.
    const lng = s.user?.preferredLanguage;
    if (lng && isSupported(lng) && lng !== i18n.language) void i18n.changeLanguage(lng);
  }, []);

  const signOut = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      session.clear();
    }
  }, []);

  const setUser = useCallback((u: UserView) => setUserState(u), []);
  const hasRole = useCallback((role: Role) => Boolean(user?.roles.includes(role)), [user]);

  // Keep the account's preferred language in step with the header selector.
  useEffect(() => {
    const onChange = (lng: string) => {
      if (user && isSupported(lng) && user.preferredLanguage !== lng) {
        accountApi.update({ preferredLanguage: lng }).then(setUserState).catch(() => undefined);
      }
    };
    i18n.on('languageChanged', onChange);
    return () => i18n.off('languageChanged', onChange);
  }, [user]);

  const value = useMemo(
    () => ({ status, user, signIn, signOut, setUser, hasRole }),
    [status, user, signIn, signOut, setUser, hasRole],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
