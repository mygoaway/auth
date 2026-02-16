import client from './client';

export const adminApi = {
  getDashboard: () =>
    client.get('/admin/dashboard'),

  searchUsers: (params = {}) => {
    const query = new URLSearchParams();
    if (params.keyword) query.append('keyword', params.keyword);
    if (params.status) query.append('status', params.status);
    query.append('page', params.page || 0);
    query.append('size', params.size || 20);
    return client.get(`/admin/users?${query.toString()}`);
  },

  updateUserRole: (userId, role) =>
    client.patch(`/admin/users/${userId}/role?role=${role}`),

  updateUserStatus: (userId, status) =>
    client.patch(`/admin/users/${userId}/status?status=${status}`),

  getLoginStats: () =>
    client.get('/admin/stats/logins'),

  getSecurityEvents: () =>
    client.get('/admin/security/events'),

  getSupportStats: () =>
    client.get('/admin/stats/support'),
};
