import { useTranslation } from 'react-i18next';
import { BadgeCheck, CalendarClock, IndianRupee, MapPin, Search, QrCode } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { buttonClasses } from '@/components/ui/Button';
import { usePublicConfig } from '@/services/platformApi';
import { formatINR } from '@/utils/format';
import { illustratePlatformFee, paiseToRupees } from '@/utils/money';

const EXAMPLE_FEE_PAISE = 100_000; // ₹1,000

export function HomePage() {
  const { t } = useTranslation();
  const { data: config } = usePublicConfig();
  const percent = config?.commissionPercent;
  const example = percent !== undefined ? illustratePlatformFee(EXAMPLE_FEE_PAISE, percent) : null;

  const values = [
    { icon: BadgeCheck, title: t('home.valueVerifiedTitle'), body: t('home.valueVerifiedBody') },
    { icon: CalendarClock, title: t('home.valueRealtimeTitle'), body: t('home.valueRealtimeBody') },
    {
      icon: IndianRupee,
      title: t('home.valuePricingTitle'),
      body: percent !== undefined ? t('home.valuePricingBody', { percent }) : null,
    },
  ];
  const steps = [
    { icon: MapPin, text: t('home.howStep1') },
    { icon: Search, text: t('home.howStep2') },
    { icon: QrCode, text: t('home.howStep3') },
  ];

  return (
    <>
      <section className="relative overflow-hidden">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-0 -top-40 h-[480px] bg-[radial-gradient(ellipse_at_top,rgb(var(--brand-300)/0.45),transparent_65%)]"
        />
        <div className="relative mx-auto grid max-w-7xl items-center gap-12 px-4 pb-16 pt-16 sm:px-6 md:pt-24 lg:grid-cols-[1.2fr_1fr]">
          <div className="animate-fade-up">
            <p className="inline-flex rounded-full border border-brand-200 bg-brand-50 px-3 py-1 text-xs font-semibold text-brand-700">
              {t('home.heroEyebrow')}
            </p>
            <h1 className="mt-5 text-4xl font-extrabold leading-tight tracking-tight sm:text-5xl lg:text-6xl">
              {t('home.heroTitle')}
            </h1>
            <p className="mt-5 max-w-xl text-lg text-fg-muted">{t('home.heroSubtitle')}</p>
            <div className="mt-8 flex flex-wrap gap-3">
              <a href="#how" className={buttonClasses('primary', 'lg')}>
                {t('home.ctaExplore')}
              </a>
            </div>
          </div>

          {example && (
            <Card className="animate-fade-up p-6 [animation-delay:120ms] sm:p-8">
              <h2 className="text-sm font-semibold uppercase tracking-wider text-fg-muted">{t('home.exampleTitle')}</h2>
              <dl className="mt-5 space-y-3 text-sm">
                <div className="flex justify-between">
                  <dt className="text-fg-muted">{t('home.exampleService')}</dt>
                  <dd className="font-medium">{formatINR(paiseToRupees(example.serviceFeePaise))}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-fg-muted">{t('home.examplePlatform', { percent })}</dt>
                  <dd className="font-medium">{formatINR(paiseToRupees(example.platformFeePaise))}</dd>
                </div>
                <div className="flex justify-between border-t border-border pt-3 text-base">
                  <dt className="font-semibold">{t('home.exampleTotal')}</dt>
                  <dd className="font-bold text-brand-700">{formatINR(paiseToRupees(example.totalPaise))}</dd>
                </div>
              </dl>
            </Card>
          )}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 sm:px-6" aria-label={t('home.valuePricingTitle')}>
        <div className="grid gap-5 md:grid-cols-3">
          {values.map(({ icon: Icon, title, body }) => (
            <Card key={title} className="p-6 transition hover:-translate-y-0.5 hover:shadow-lift">
              <span className="inline-flex h-11 w-11 items-center justify-center rounded-xl bg-brand-100 text-brand-700">
                <Icon className="h-5 w-5" aria-hidden="true" />
              </span>
              <h3 className="mt-4 font-semibold">{title}</h3>
              {body && <p className="mt-1.5 text-sm text-fg-muted">{body}</p>}
            </Card>
          ))}
        </div>
      </section>

      <section id="how" className="mx-auto mt-20 max-w-7xl scroll-mt-24 px-4 sm:px-6">
        <h2 className="text-2xl font-bold tracking-tight sm:text-3xl">{t('home.howTitle')}</h2>
        <ol className="mt-8 grid gap-5 md:grid-cols-3">
          {steps.map(({ icon: Icon, text }, i) => (
            <li key={text} className="flex gap-4 rounded-2xl border border-dashed border-border p-5">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-600 text-sm font-bold text-white">
                {i + 1}
              </span>
              <div className="flex items-start gap-2">
                <Icon className="mt-0.5 h-5 w-5 shrink-0 text-brand-600" aria-hidden="true" />
                <p className="text-sm">{text}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>
    </>
  );
}
