import { Link, useNavigate, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Users } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button, buttonClasses } from '@/components/ui/Button';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { useAuth } from '@/contexts/AuthContext';
import { AuthCard } from '@/features/auth/AuthCard';
import { useApiForm } from '@/hooks/useApiForm';
import { accountApi } from '@/services/authApi';
import { invitationApi } from '@/services/teamApi';

export function AcceptInvitationPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const navigate = useNavigate();
  const { status, user, setUser } = useAuth();
  const form = useApiForm();
  const preview = useQuery({ queryKey: ['invite', token], queryFn: () => invitationApi.preview(token), enabled: Boolean(token), retry: false });
  const here = encodeURIComponent(`/staff/invitations/accept?token=${encodeURIComponent(token)}`);

  const accept = async () => {
    const ok = await form.submit(() => invitationApi.accept(token));
    if (ok) {
      setUser(await accountApi.me());
      navigate('/staff', { replace: true });
    }
  };

  if (!token) return <ErrorState error={null} />;
  if (preview.isLoading || status === 'loading') return <LoadingState />;
  if (preview.error) return <ErrorState error={preview.error} />;
  const p = preview.data!;
  const wrongAccount = status === 'authenticated' && user?.email?.toLowerCase() !== p.email.toLowerCase();

  return (
    <AuthCard title={t('team.joinTitle', { business: p.organizationName })} subtitle={t('team.joinSubtitle', { email: p.email })}>
      <div className="space-y-4">
        <div className="flex items-center gap-3 rounded-xl bg-brand-50 p-4 text-brand-900">
          <Users className="h-5 w-5" aria-hidden="true" />
          <span className="text-sm">{t('team.joinAs', { name: p.displayName })}</span>
        </div>
        {form.formError && <Alert tone="error">{form.formError}</Alert>}
        {status !== 'authenticated' ? (
          <div className="flex flex-wrap gap-2">
            <Link to={`/signin?next=${here}`} className={buttonClasses('primary')}>{t('nav.signIn')}</Link>
            <Link to={`/register?next=${here}`} className={buttonClasses('secondary')}>{t('nav.register')}</Link>
          </div>
        ) : wrongAccount ? (
          <Alert tone="error">{t('errors.INVITATION_EMAIL_MISMATCH')}</Alert>
        ) : (
          <Button onClick={() => void accept()} loading={form.submitting} className="w-full">{t('team.accept')}</Button>
        )}
      </div>
    </AuthCard>
  );
}
