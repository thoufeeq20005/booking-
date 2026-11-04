import { apiGet, apiPatch, apiPost, CLIENT_HEADER, type Session, type UserView } from './apiClient';

export interface OtpSent {
  expiresInSeconds: number;
  resendAfterSeconds: number;
}

export interface TwoFactorSetup {
  secret: string;
  otpauthUri: string;
}

export const authApi = {
  register: (body: { fullName: string; email: string; password: string; preferredLanguage?: string }) =>
    apiPost<{ session: Session; verificationEmailSent: boolean }>('/auth/register', body),
  login: (email: string, password: string) => apiPost<Session>('/auth/login', { email, password }),
  completeTwoFactor: (challengeToken: string, code: string) =>
    apiPost<Session>('/auth/login/two-factor', { challengeToken, code }),
  sendOtp: (phone: string) => apiPost<OtpSent>('/auth/otp/send', { phone }),
  otpLogin: (body: { phone: string; code: string; fullName?: string; preferredLanguage?: string }) =>
    apiPost<Session>('/auth/otp/login', body),
  google: (idToken: string) => apiPost<Session>('/auth/google', { idToken }),
  logout: () => apiPost<void>('/auth/logout', null, CLIENT_HEADER),
  verifyEmail: (token: string) => apiPost<void>('/auth/email/verify', { token }),
  forgotPassword: (email: string) => apiPost<void>('/auth/password/forgot', { email }),
  resetPassword: (token: string, newPassword: string) => apiPost<void>('/auth/password/reset', { token, newPassword }),
};

export const accountApi = {
  me: () => apiGet<UserView>('/account/me'),
  update: (body: { fullName?: string; preferredLanguage?: string }) => apiPatch<UserView>('/account/me', body),
  resendVerification: () => apiPost<void>('/account/email/resend-verification'),
  changePassword: (body: { currentPassword?: string; newPassword: string }) =>
    apiPost<Session>('/account/password', body),
  sendPhoneCode: (phone: string) => apiPost<OtpSent>('/account/phone/send-code', { phone }),
  verifyPhone: (phone: string, code: string) => apiPost<UserView>('/account/phone/verify', { phone, code }),
  setupTwoFactor: () => apiPost<TwoFactorSetup>('/account/two-factor/setup'),
  enableTwoFactor: (code: string) => apiPost<UserView>('/account/two-factor/enable', { code }),
  disableTwoFactor: (code: string, password?: string) =>
    apiPost<UserView>('/account/two-factor/disable', { code, password }),
  logoutAll: () => apiPost<void>('/account/logout-all'),
};
