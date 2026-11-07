import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AppRoutes } from '@/routes/AppRoutes';
import { ApiError } from '@/services/apiClient';
import { platformApi, type PublicConfig } from '@/services/platformApi';

const config: PublicConfig = {
  currency: 'INR',
  commissionPercent: 5,
  supportedLanguages: ['en', 'ta', 'hi', 'ml', 'te', 'kn'],
  defaultLanguage: 'en',
  timezone: 'Asia/Kolkata',
  support: { workingDays: ['MON', 'SAT'], opensAt: '09:00:00', closesAt: '18:00:00', timezone: 'Asia/Kolkata', available: true },
};

beforeEach(() => {
  vi.restoreAllMocks();
  vi.spyOn(platformApi, 'getPublicConfig').mockResolvedValue(config);
});

describe('HomePage', () => {
  it('shows the price breakdown using the commission from the API', async () => {
    renderWithProviders(<AppRoutes />);
    expect(await screen.findByText('BookEasy platform fee (5%)')).toBeInTheDocument();
    expect(screen.getByText('₹1,000')).toBeInTheDocument();
    expect(screen.getByText('₹50')).toBeInTheDocument();
    expect(screen.getByText('₹1,050')).toBeInTheDocument();
  });

  it('does not invent support contacts when none are configured', async () => {
    renderWithProviders(<AppRoutes />);
    expect(await screen.findByText('Contact details will be published soon')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /@/ })).not.toBeInTheDocument();
  });

  it('shows configured support contacts', async () => {
    vi.spyOn(platformApi, 'getPublicConfig').mockResolvedValue({
      ...config,
      support: { ...config.support, email: 'help@example.test', phone: '+911234567890' },
    });
    renderWithProviders(<AppRoutes />);
    expect(await screen.findByRole('link', { name: 'help@example.test' })).toHaveAttribute('href', 'mailto:help@example.test');
  });
});

describe('StatusPage', () => {
  it('shows live API and database health', async () => {
    vi.spyOn(platformApi, 'getHealth').mockResolvedValue({ status: 'UP', database: 'UP', checkedAt: '2026-11-04T10:00:00Z' });
    renderWithProviders(<AppRoutes />, { route: '/status' });
    expect(await screen.findAllByText('Operational')).toHaveLength(2);
  });

  it('reports a database outage', async () => {
    vi.spyOn(platformApi, 'getHealth').mockResolvedValue({ status: 'DEGRADED', database: 'DOWN', checkedAt: '2026-11-04T10:00:00Z' });
    renderWithProviders(<AppRoutes />, { route: '/status' });
    expect(await screen.findByText('Unavailable')).toBeInTheDocument();
  });

  it('shows a translated error with retry when the server is unreachable', async () => {
    vi.spyOn(platformApi, 'getHealth').mockRejectedValue(new ApiError('NETWORK_ERROR', 'down', null));
    renderWithProviders(<AppRoutes />, { route: '/status' });
    expect(await screen.findByText(/can't reach BookEasy/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});

describe('routing', () => {
  it('renders a 404 page for unknown routes', async () => {
    renderWithProviders(<AppRoutes />, { route: '/does-not-exist' });
    expect(await screen.findByText('Page not found')).toBeInTheDocument();
  });
});
