import axios from 'axios';

const API_BASE_URL = `${import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}/api/v1`;

const client = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // Enable cookies for cross-origin requests
});

// Helper to get the correct storage
const getStorage = () => {
  if (sessionStorage.getItem('accessToken')) {
    return sessionStorage;
  }
  return localStorage;
};

const clearAllTokens = () => {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  sessionStorage.removeItem('accessToken');
  sessionStorage.removeItem('refreshToken');
};

// Request interceptor - attach access token
client.interceptors.request.use((config) => {
  const storage = getStorage();
  const token = storage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor - handle token refresh
client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // 인증 API(로그인/회원가입 등)의 401은 refresh 시도하지 않음
    const isAuthRequest = originalRequest.url?.includes('/auth/email/login')
      || originalRequest.url?.includes('/auth/email/signup')
      || originalRequest.url?.includes('/auth/password/');

    if (error.response?.status === 401 && !originalRequest._retry && !isAuthRequest) {
      originalRequest._retry = true;

      try {
        const storage = getStorage();
        const refreshToken = storage.getItem('refreshToken');
        if (!refreshToken) {
          throw new Error('No refresh token');
        }

        const response = await axios.post(`${API_BASE_URL}/auth/refresh`, {
          refreshToken,
        });

        const { accessToken, refreshToken: newRefreshToken } = response.data;
        storage.setItem('accessToken', accessToken);
        storage.setItem('refreshToken', newRefreshToken);

        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return client(originalRequest);
      } catch (refreshError) {
        clearAllTokens();
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default client;
