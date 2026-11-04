import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckboxField } from '@/components/ui/FormFields';
import { notificationApi, type Preferences } from '@/services/engagementApi';

export function NotificationPreferencesSection() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ['prefs'], queryFn: notificationApi.preferences });
  if (!data) return null;
  const set = (k: keyof Preferences) => (v: boolean) =>
    void notificationApi.savePreferences({ ...data, [k]: v }).then((p) => qc.setQueryData(['prefs'], p));
  return (
    <div className="space-y-3">
      <p className="text-sm text-fg-muted">{t('notify.prefsIntro')}</p>
      <CheckboxField label={t('notify.pref.email')} checked={data.emailEnabled} onChange={set('emailEnabled')} />
      <CheckboxField label={t('notify.pref.reminders')} checked={data.remindersEnabled} onChange={set('remindersEnabled')} />
      <CheckboxField label={t('notify.pref.sms')} checked={data.smsEnabled} onChange={set('smsEnabled')} hint={t('notify.pref.soon')} />
      <CheckboxField label={t('notify.pref.whatsapp')} checked={data.whatsappEnabled} onChange={set('whatsappEnabled')} hint={t('notify.pref.soon')} />
    </div>
  );
}
