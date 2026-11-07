import type { Config } from 'tailwindcss';

// Colours are CSS variables (see src/styles/index.css) so light/dark themes swap without class duplication.
const token = (name: string) => `rgb(var(--${name}) / <alpha-value>)`;

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: ['class', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: token('brand-50'),
          100: token('brand-100'),
          200: token('brand-200'),
          300: token('brand-300'),
          400: token('brand-400'),
          500: token('brand-500'),
          600: token('brand-600'),
          700: token('brand-700'),
          800: token('brand-800'),
          900: token('brand-900'),
        },
        surface: token('surface'),
        'surface-muted': token('surface-muted'),
        canvas: token('canvas'),
        border: token('border'),
        fg: token('fg'),
        'fg-muted': token('fg-muted'),
        success: token('success'),
        warning: token('warning'),
        danger: token('danger'),
      },
      fontFamily: {
        sans: [
          'Inter',
          'Noto Sans Tamil',
          'Noto Sans Devanagari',
          'Noto Sans Malayalam',
          'Noto Sans Telugu',
          'Noto Sans Kannada',
          'system-ui',
          'sans-serif',
        ],
      },
      borderRadius: { xl: '0.875rem', '2xl': '1.25rem' },
      boxShadow: {
        card: '0 1px 2px rgb(16 24 40 / 0.04), 0 2px 8px -2px rgb(16 24 40 / 0.06)',
        lift: '0 12px 28px -10px rgb(17 24 39 / 0.18)',
      },
      keyframes: {
        'fade-up': { from: { opacity: '0', transform: 'translateY(8px)' }, to: { opacity: '1', transform: 'none' } },
      },
      animation: { 'fade-up': 'fade-up 0.4s ease-out both' },
    },
  },
  plugins: [],
} satisfies Config;
