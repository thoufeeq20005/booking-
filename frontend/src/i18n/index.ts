import i18n from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import { initReactI18next } from 'react-i18next';
import en from './locales/en.json';
import hi from './locales/hi.json';
import kn from './locales/kn.json';
import ml from './locales/ml.json';
import ta from './locales/ta.json';
import te from './locales/te.json';

export const LANGUAGES = [
  { code: 'en', nativeName: 'English', intlLocale: 'en-IN' },
  { code: 'ta', nativeName: 'தமிழ்', intlLocale: 'ta-IN' },
  { code: 'hi', nativeName: 'हिन्दी', intlLocale: 'hi-IN' },
  { code: 'ml', nativeName: 'മലയാളം', intlLocale: 'ml-IN' },
  { code: 'te', nativeName: 'తెలుగు', intlLocale: 'te-IN' },
  { code: 'kn', nativeName: 'ಕನ್ನಡ', intlLocale: 'kn-IN' },
] as const;

export type LanguageCode = (typeof LANGUAGES)[number]['code'];

export const LANGUAGE_STORAGE_KEY = 'bookeasy.language';

export function intlLocale(code: string): string {
  return LANGUAGES.find((l) => l.code === code)?.intlLocale ?? 'en-IN';
}

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
      ta: { translation: ta },
      hi: { translation: hi },
      ml: { translation: ml },
      te: { translation: te },
      kn: { translation: kn },
    },
    supportedLngs: LANGUAGES.map((l) => l.code),
    nonExplicitSupportedLngs: true,
    fallbackLng: 'en',
    interpolation: { escapeValue: false },
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: LANGUAGE_STORAGE_KEY,
      caches: ['localStorage'],
    },
    returnNull: false,
  });

i18n.on('languageChanged', (lng) => {
  document.documentElement.lang = lng;
});

export default i18n;
