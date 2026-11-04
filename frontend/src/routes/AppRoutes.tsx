import { lazy } from 'react';
import { Route, Routes } from 'react-router';
import { PublicLayout } from '@/layouts/PublicLayout';
import { HomePage } from '@/pages/HomePage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { StatusPage } from '@/pages/StatusPage';
import type { Role } from '@/services/apiClient';
import { RequireAuth } from './RequireAuth';

// Loaded on demand so the home page stays light.
const named = <K extends string>(load: () => Promise<Record<K, React.ComponentType>>, name: K) =>
  lazy(() => load().then((m) => ({ default: m[name] })));

const SignInPage = named(() => import('@/pages/auth/SignInPage'), 'SignInPage');
const RegisterPage = named(() => import('@/pages/auth/RegisterPage'), 'RegisterPage');
const VerifyEmailPage = named(() => import('@/pages/auth/VerifyEmailPage'), 'VerifyEmailPage');
const ForgotPasswordPage = named(() => import('@/pages/auth/ForgotPasswordPage'), 'ForgotPasswordPage');
const ResetPasswordPage = named(() => import('@/pages/auth/ResetPasswordPage'), 'ResetPasswordPage');
const AccountPage = named(() => import('@/pages/account/AccountPage'), 'AccountPage');
const RegisterBusinessPage = named(() => import('@/pages/business/RegisterBusinessPage'), 'RegisterBusinessPage');
const MyBusinessesPage = named(() => import('@/pages/owner/MyBusinessesPage'), 'MyBusinessesPage');
const OrganizationSetupPage = named(() => import('@/pages/owner/OrganizationSetupPage'), 'OrganizationSetupPage');
const AdminOrganizationsPage = named(() => import('@/pages/admin/AdminOrganizationsPage'), 'AdminOrganizationsPage');
const AdminOrganizationReviewPage = named(
  () => import('@/pages/admin/AdminOrganizationReviewPage'),
  'AdminOrganizationReviewPage',
);
const AdminCategoriesPage = named(() => import('@/pages/admin/AdminCategoriesPage'), 'AdminCategoriesPage');

const guard = (page: React.ReactNode, roles?: Role[]) => <RequireAuth roles={roles}>{page}</RequireAuth>;
const OWNER: Role[] = ['BUSINESS_OWNER'];
const ADMIN: Role[] = ['SUPER_ADMIN'];

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<HomePage />} />
        <Route path="status" element={<StatusPage />} />
        <Route path="signin" element={<SignInPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="verify-email" element={<VerifyEmailPage />} />
        <Route path="forgot-password" element={<ForgotPasswordPage />} />
        <Route path="reset-password" element={<ResetPasswordPage />} />
        <Route
          path="account"
          element={
            <RequireAuth>
              <AccountPage />
            </RequireAuth>
          }
        />
        <Route path="business/register" element={guard(<RegisterBusinessPage />)} />
        <Route path="owner/organizations" element={guard(<MyBusinessesPage />, OWNER)} />
        <Route path="owner/organizations/:id" element={guard(<OrganizationSetupPage />, OWNER)} />
        <Route path="admin/organizations" element={guard(<AdminOrganizationsPage />, ADMIN)} />
        <Route path="admin/organizations/:id" element={guard(<AdminOrganizationReviewPage />, ADMIN)} />
        <Route path="admin/categories" element={guard(<AdminCategoriesPage />, ADMIN)} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
