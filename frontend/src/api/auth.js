import client from './client';

export const authApi = {
  sendVerification: (email, type = 'SIGNUP') =>
    client.post('/auth/email/send-verification', { email, type }),

  sendPasswordResetVerification: (email) =>
    client.post('/auth/email/send-verification', { email, type: 'PASSWORD_RESET' }),

  sendEmailChangeVerification: (email) =>
    client.post('/auth/email/send-verification', { email, type: 'EMAIL_CHANGE' }),

  verifyCode: (email, code, type = 'SIGNUP') =>
    client.post('/auth/email/verify', { email, code, type }),

  signup: (data) =>
    client.post('/auth/email/signup', data),

  login: (email, password) =>
    client.post('/auth/email/login', { email, password }),

  refresh: (refreshToken) =>
    client.post('/auth/refresh', { refreshToken }),

  logout: (accessToken, refreshToken) =>
    client.post('/auth/logout', { accessToken, refreshToken }),

  logoutAll: () =>
    client.post('/auth/logout-all'),

  changePassword: (currentPassword, newPassword) =>
    client.post('/auth/password/change', { currentPassword, newPassword }),

  getRecoveryAccounts: (tokenId, recoveryEmail) =>
    client.post('/auth/password/reset/accounts', { tokenId, recoveryEmail }),

  resetPassword: (tokenId, recoveryEmail, loginEmail, newPassword) =>
    client.post('/auth/password/reset', { tokenId, recoveryEmail, loginEmail, newPassword }),

  analyzePassword: (password) =>
    client.post('/auth/password/analyze', { password }),
};

export const userApi = {
  getProfile: () =>
    client.get('/users/profile'),

  updateNickname: (nickname) =>
    client.patch('/users/profile/nickname', { nickname }),

  updatePhone: (phone, tokenId) =>
    client.patch('/users/profile/phone', { phone, tokenId }),

  updateRecoveryEmail: (recoveryEmail, tokenId) =>
    client.patch('/users/profile/recovery-email', { recoveryEmail, tokenId }),

  deleteAccount: () =>
    client.delete('/users/me'),

  cancelDeletion: () =>
    client.post('/users/me/cancel-deletion'),

  getChannelsStatus: () =>
    client.get('/users/channels'),

  unlinkChannel: (channelCode) =>
    client.delete(`/users/channels/${channelCode}`),

  getLoginHistory: (limit = 10) =>
    client.get(`/users/login-history?limit=${limit}`),

  getActiveSessions: () =>
    client.get('/users/sessions'),

  revokeSession: (sessionId) =>
    client.delete(`/users/sessions/${sessionId}`),

  getSecurityDashboard: () =>
    client.get('/users/security/dashboard'),

  getWeeklyActivity: () =>
    client.get('/users/activity/weekly'),

  getTrustedDevices: () =>
    client.get('/users/devices/trusted'),

  isCurrentDeviceTrusted: () =>
    client.get('/users/devices/trusted/current'),

  trustCurrentDevice: () =>
    client.post('/users/devices/trusted'),

  removeTrustedDevice: (deviceId) =>
    client.delete(`/users/devices/trusted/${deviceId}`),

  removeAllTrustedDevices: () =>
    client.delete('/users/devices/trusted'),

  getSuspiciousActivity: () =>
    client.get('/users/security/suspicious'),

  getSecuritySettings: () =>
    client.get('/users/security/settings'),

  updateLoginNotification: (enabled) =>
    client.patch('/users/security/settings/login-notification', { enabled }),

  updateSuspiciousNotification: (enabled) =>
    client.patch('/users/security/settings/suspicious-notification', { enabled }),

  unlockAccount: () =>
    client.post('/users/security/unlock'),
};

export const phoneApi = {
  sendVerification: (phone) =>
    client.post('/phone/send-verification', { phone }),

  verifyCode: (phone, code) =>
    client.post('/phone/verify', { phone, code }),
};

export const emailApi = {
  sendVerification: (email, type = 'SIGNUP') =>
    client.post('/auth/email/send-verification', { email, type }),

  verifyCode: (email, code, type = 'SIGNUP') =>
    client.post('/auth/email/verify', { email, code, type }),
};

export const twoFactorApi = {
  getStatus: () =>
    client.get('/2fa/status'),

  setup: () =>
    client.post('/2fa/setup'),

  enable: (code) =>
    client.post('/2fa/enable', { code }),

  disable: (code) =>
    client.post('/2fa/disable', { code }),

  verify: (code) =>
    client.post('/2fa/verify', { code }),

  regenerateBackupCodes: (code) =>
    client.post('/2fa/backup-codes/regenerate', { code }),
};

export const oauth2Api = {
  prepareLink: (provider) =>
    client.post(`/oauth2/link/prepare/${provider}`),
};
