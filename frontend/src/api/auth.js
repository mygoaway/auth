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

  resetPassword: (tokenId, email, newPassword) =>
    client.post('/auth/password/reset', { tokenId, email, newPassword }),
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

  getChannelsStatus: () =>
    client.get('/users/channels'),

  registerPassword: (password) =>
    client.post('/users/register-password', { password }),

  unlinkChannel: (channelCode) =>
    client.delete(`/users/channels/${channelCode}`),

  getLoginHistory: (limit = 10) =>
    client.get(`/users/login-history?limit=${limit}`),
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
