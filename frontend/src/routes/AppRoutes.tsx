import { lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { useAuth } from '@/contexts/AuthContext';
import { DashboardLayout } from '@/layouts/DashboardLayout';
import { PublicLayout } from '@/layouts/PublicLayout';
import { HomePage } from '@/pages/HomePage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { StatusPage } from '@/pages/StatusPage';
import type { Role } from '@/services/apiClient';
import { homeFor } from '@/services/dashboardApi';
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
const AdminUsersPage = named(() => import('@/pages/admin/AdminUsersPage'), 'AdminUsersPage');
const AdminAuditPage = named(() => import('@/pages/admin/AdminAuditPage'), 'AdminAuditPage');
const AdminDashboardPage = named(() => import('@/pages/dashboard/AdminDashboardPage'), 'AdminDashboardPage');
const OwnerDashboardPage = named(() => import('@/pages/dashboard/OwnerDashboardPage'), 'OwnerDashboardPage');
const CustomerOverviewPage = named(() => import('@/pages/dashboard/CustomerOverviewPage'), 'CustomerOverviewPage');
const SearchPage = named(() => import('@/pages/discovery/SearchPage'), 'SearchPage');
const BusinessPage = named(() => import('@/pages/discovery/BusinessPage'), 'BusinessPage');
const AcceptInvitationPage = named(() => import('@/pages/staff/AcceptInvitationPage'), 'AcceptInvitationPage');
const StaffOverviewPage = named(() => import('@/pages/dashboard/StaffOverviewPage'), 'StaffOverviewPage');

/** /dashboard sends each person to the area of their most powerful role. */
function DashboardRedirect() {
  const { user } = useAuth();
  return <Navigate to={homeFor(user?.roles ?? [])} replace />;
}

const guard = (page: React.ReactNode, roles?: Role[]) => <RequireAuth roles={roles}>{page}</RequireAuth>;
const OWNER: Role[] = ['BUSINESS_OWNER'];
const ADMIN: Role[] = ['SUPER_ADMIN'];
const STAFF: Role[] = ['STAFF'];

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<HomePage />} />
        <Route path="status" element={<StatusPage />} />
        <Route path="search" element={<SearchPage />} />
        <Route path="b/:slug" element={<BusinessPage />} />
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
        <Route path="dashboard" element={guard(<DashboardRedirect />)} />
        <Route path="customer" element={guard(<DashboardLayout area="customer" />)}>
          <Route index element={<CustomerOverviewPage />} />
        </Route>
        <Route path="owner" element={guard(<DashboardLayout area="owner" />, OWNER)}>
          <Route index element={<OwnerDashboardPage />} />
          <Route path="organizations" element={<MyBusinessesPage />} />
          <Route path="organizations/:id" element={<OrganizationSetupPage />} />
        </Route>
        <Route path="staff/invitations/accept" element={<AcceptInvitationPage />} />
        <Route path="staff" element={guard(<DashboardLayout area="staff" />, STAFF)}>
          <Route index element={<StaffOverviewPage />} />
        </Route>
        <Route path="admin" element={guard(<DashboardLayout area="admin" />, ADMIN)}>
          <Route index element={<AdminDashboardPage />} />
          <Route path="organizations" element={<AdminOrganizationsPage />} />
          <Route path="organizations/:id" element={<AdminOrganizationReviewPage />} />
          <Route path="categories" element={<AdminCategoriesPage />} />
          <Route path="users" element={<AdminUsersPage />} />
          <Route path="audit" element={<AdminAuditPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
