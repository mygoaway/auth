import client from './client';

export const authApi = {
  sendVerification: (email) =>
    client.post('/auth/email/send-verification', { email }),

  verifyCode: (email, code) =>
    client.post('/auth/email/verify', { email, code }),

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

  sendPasswordResetVerification: (email) =>
    client.post('/auth/email/send-verification', { email }),
};

export const userApi = {
  getProfile: () =>
    client.get('/users/profile'),

  updateNickname: (nickname) =>
    client.patch('/users/profile/nickname', { nickname }),

  updatePhone: (phone) =>
    client.patch('/users/profile/phone', { phone }),

  updateRecoveryEmail: (recoveryEmail) =>
    client.patch('/users/profile/recovery-email', { recoveryEmail }),
};
