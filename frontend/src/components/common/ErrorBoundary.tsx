import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Translation } from 'react-i18next';
import { AlertTriangle } from 'lucide-react';

interface State {
  hasError: boolean;
}

export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled UI error', error, info.componentStack);
  }

  render() {
    if (!this.state.hasError) return this.props.children;
    return (
      <Translation>
        {(t) => (
          <div role="alert" className="mx-auto flex max-w-lg flex-col items-center gap-3 px-4 py-24 text-center">
            <AlertTriangle className="h-10 w-10 text-danger" aria-hidden="true" />
            <h1 className="text-xl font-bold">{t('errors.boundaryTitle')}</h1>
            <p className="text-fg-muted">{t('errors.boundaryBody')}</p>
            <button
              type="button"
              className="mt-2 rounded-xl bg-brand-600 px-5 py-2.5 font-semibold text-white"
              onClick={() => window.location.reload()}
            >
              {t('states.retry')}
            </button>
          </div>
        )}
      </Translation>
    );
  }
}
