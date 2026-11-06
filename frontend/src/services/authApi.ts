import { apiGet, apiPatch, apiPost, CLIENT_HEADER, type Session, type UserView } from './apiClient';

export interface OtpSent {
  expiresInSeconds: number;
  resendAfterSeconds: number;
  /** Only in development without an SMS provider: the code, shown on screen instead of an SMS. */
  demoCode?: string;
}

export interface TwoFactorSetup {
  secret: string;
  otpauthUri: string;
}

export const authApi = {
  register: (body: { fullName: string; email: string; password: string; preferredLanguage?: string }) =>
    apiPost<{ session: Session }>('/auth/register', body),
  login: (email: string, password: string) => apiPost<Session>('/auth/login', { email, password }),
  completeTwoFactor: (challengeToken: string, code: string) =>
    apiPost<Session>('/auth/login/two-factor', { challengeToken, code }),
  sendEmailCode: (email: string) => apiPost<OtpSent>('/auth/email-code/send', { email }),
  emailCodeLogin: (email: string, code: string) => apiPost<Session>('/auth/email-code/login', { email, code }),
  google: (idToken: string) => apiPost<Session>('/auth/google', { idToken }),
  logout: () => apiPost<void>('/auth/logout', null, CLIENT_HEADER),
  forgotPassword: (email: string) => apiPost<void>('/auth/password/forgot', { email }),
  resetPassword: (token: string, newPassword: string) => apiPost<void>('/auth/password/reset', { token, newPassword }),
};

export const accountApi = {
  me: () => apiGet<UserView>('/account/me'),
  update: (body: { fullName?: string; preferredLanguage?: string }) => apiPatch<UserView>('/account/me', body),
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
