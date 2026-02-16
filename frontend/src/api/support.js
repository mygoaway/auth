import client from './client';

export const supportApi = {
  getPosts: (params = {}) => {
    const query = new URLSearchParams();
    if (params.category) query.append('category', params.category);
    if (params.status) query.append('status', params.status);
    query.append('page', params.page || 0);
    query.append('size', params.size || 10);
    return client.get(`/support/posts?${query.toString()}`);
  },

  getPost: (postId) =>
    client.get(`/support/posts/${postId}`),

  createPost: (data) =>
    client.post('/support/posts', data),

  updatePost: (postId, data) =>
    client.put(`/support/posts/${postId}`, data),

  deletePost: (postId) =>
    client.delete(`/support/posts/${postId}`),

  createComment: (postId, content) =>
    client.post(`/support/posts/${postId}/comments`, { content }),

  deleteComment: (postId, commentId) =>
    client.delete(`/support/posts/${postId}/comments/${commentId}`),

  updatePostStatus: (postId, status) =>
    client.patch(`/support/posts/${postId}/status`, { status }),
};
