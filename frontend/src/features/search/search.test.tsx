import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { installFakeApi, reply } from '@/test/fakeApi';
import { renderWithProviders } from '@/test/renderWithProviders';
import { SearchButton } from './SearchOverlay';

const movie = { kind: 'MOVIE', title: 'Signal Thirteen', detail: 'UA · Tamil, Telugu', href: '/movies/7', posterFrom: '#111111', posterTo: '#222222' };
const cafe = { kind: 'BUSINESS', title: 'Brew Theory', category: { en: 'Café' }, detail: 'RS Puram, Coimbatore', href: '/b/brew-theory', imageUrl: '/api/v1/public/files/abc' };
const camp = { kind: 'EVENT', title: 'Free dental camp', detail: 'Pearl Dental', href: '/b/pearl-dental', until: '2026-11-30' };
const biryani = { kind: 'BUSINESS', title: 'Biryani House', category: { en: 'Restaurant' }, detail: 'Gandhipuram', href: '/b/biryani-house' };

describe('search window', () => {
  it('shows trending items, filters by tab and searches as you type', async () => {
    const requests = installFakeApi((r) => {
      if (r.url === '/auth/refresh') return reply.fail(401, 'SESSION_EXPIRED');
      if (r.url === '/public/categories') {
        return reply.ok([{ id: 9, slug: 'dining', name: { en: 'Dining' }, icon: 'utensils', bookingWorkflow: 'DINING', active: true, sortOrder: 1, subcategories: [] }]);
      }
      if (r.url === '/public/quick-search') {
        if (r.params.q) return reply.ok({ trending: false, items: [biryani] });
        if (r.params.tab === 'dining') return reply.ok({ trending: true, items: [cafe] });
        return reply.ok({ trending: true, items: [movie, cafe, camp] });
      }
      return undefined;
    });
    renderWithProviders(<SearchButton />);

    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText('Signal Thirteen')).toBeInTheDocument();
    expect(within(dialog).getByText(/Trending in/)).toBeInTheDocument();
    expect(within(dialog).getByRole('link', { name: /Brew Theory/ })).toHaveAttribute('href', '/b/brew-theory');
    expect(within(dialog).getByText(/Café · RS Puram/)).toBeInTheDocument();
    expect(within(dialog).getByText(/Event · till/)).toBeInTheDocument();
    expect(within(dialog).getByRole('searchbox')).toHaveAttribute('placeholder', expect.stringMatching(/^Search for '/));

    await userEvent.click(await within(dialog).findByRole('tab', { name: 'Dining' }));
    await waitFor(() => expect(within(dialog).queryByText('Signal Thirteen')).not.toBeInTheDocument());
    expect(within(dialog).getByText('Brew Theory')).toBeInTheDocument();

    await userEvent.type(within(dialog).getByRole('searchbox'), 'biry');
    expect(await within(dialog).findByText('Biryani House')).toBeInTheDocument();
    expect(within(dialog).getByText("Results for 'biry'")).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: /See all results for 'biry'/ })).toBeInTheDocument();
    expect(requests.some((r) => r.url === '/public/quick-search' && r.params.q === 'biry' && r.params.tab === 'dining')).toBe(true);

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
