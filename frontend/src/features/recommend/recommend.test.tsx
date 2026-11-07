import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { installFakeApi, reply } from '@/test/fakeApi';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ForYouSection } from './ForYouSection';

const offer = (id: string, type: string, title: string, extra = {}) => ({
  id, type, businessName: `Biz ${id}`, slug: `biz-${id}`, category: 'Gym', title, distanceKm: 1.2, rating: 4.5, reviewCount: 8, ...extra,
});

describe('picked for you nearby', () => {
  it('shows AI picks with their reasons and asks again for a quick-pick topic', async () => {
    const requests = installFakeApi((r) => {
      if (r.url === '/auth/refresh') return reply.fail(401, 'SESSION_EXPIRED');
      if (r.url === '/public/recommendations') {
        return r.params.q
          ? reply.ok({ source: 'RULES', items: [{ offer: offer('p2', 'FREE', 'Free health check-up camp', { category: 'General clinic' }),
            headline: 'Free health check-up camp', reason: 'General clinic · 1.2 km away' }] })
          : reply.ok({ source: 'AI', intro: 'Two good deals near you this evening.', items: [
            { offer: offer('p1', 'PACKAGE', 'Pay for 6 months, get 6 months free'), headline: '12 months of gym for the price of 6',
              reason: 'A big saving if you train regularly, just 1.2 km away.' },
            { offer: offer('d3', 'DISCOUNT', 'Up to 20% off the bill', { percentOff: 20, category: 'Café' }), headline: 'Coffee for less',
              reason: '20% off at a well-rated café nearby.' }] });
      }
      return undefined;
    });
    renderWithProviders(<ForYouSection />);

    expect(await screen.findByText('Two good deals near you this evening.')).toBeInTheDocument();
    const gym = screen.getByRole('link', { name: /12 months of gym/ });
    expect(gym).toHaveAttribute('href', '/b/biz-p1');
    expect(within(gym).getByText('Package deal')).toBeInTheDocument();
    expect(screen.getByText('20% off')).toBeInTheDocument();
    expect(screen.getByText(/Picked by AI from real offers/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Free health check-up' }));
    expect(await screen.findByRole('link', { name: /Free health check-up camp/ })).toBeInTheDocument();
    expect(screen.getByText(/Sorted by savings, ratings and distance/)).toBeInTheDocument();
    await waitFor(() => expect(requests.some((r) => r.url === '/public/recommendations' && r.params.q === 'Free health check-up')).toBe(true));
  });
});
