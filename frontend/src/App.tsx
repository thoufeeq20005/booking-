import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router';
import { ErrorBoundary } from '@/components/common/ErrorBoundary';
import { AuthProvider } from '@/contexts/AuthContext';
import { ThemeProvider } from '@/contexts/ThemeContext';
import { AppRoutes } from '@/routes/AppRoutes';
import { ApiError } from '@/services/apiClient';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Client errors (4xx) won't succeed on retry; network/5xx get one more attempt.
      retry: (count, err) => count < 1 && !(err instanceof ApiError && err.status !== null && err.status < 500),
      refetchOnWindowFocus: false,
    },
  },
});

export function App() {
  return (
    <ErrorBoundary>
      <ThemeProvider>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <BrowserRouter>
              <AppRoutes />
            </BrowserRouter>
          </AuthProvider>
        </QueryClientProvider>
      </ThemeProvider>
    </ErrorBoundary>
  );
}
