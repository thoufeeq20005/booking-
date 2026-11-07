import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import i18n, { LANGUAGE_STORAGE_KEY } from '@/i18n';
import { renderWithProviders } from '@/test/renderWithProviders';
import { LanguageSelector } from './LanguageSelector';

describe('LanguageSelector', () => {
  it('offers exactly the six supported languages', () => {
    renderWithProviders(<LanguageSelector />);
    const options = screen.getAllByRole('option').map((o) => o.getAttribute('value'));
    expect(options).toEqual(['en', 'ta', 'hi', 'ml', 'te', 'kn']);
  });

  it('changes the UI language, html lang attribute and stored preference', async () => {
    renderWithProviders(<LanguageSelector />);
    await userEvent.selectOptions(screen.getByLabelText('Language'), 'ta');

    expect(i18n.language).toBe('ta');
    expect(document.documentElement.lang).toBe('ta');
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('ta');
    expect(screen.getByLabelText('மொழி')).toBeInTheDocument();

    await i18n.changeLanguage('en');
  });
});
