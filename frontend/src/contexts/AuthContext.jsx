import { createContext, useContext, useState, useEffect } from 'react';
import { authApi, userApi } from '../api/auth';

const AuthContext = createContext(null);

// Helper to get/set tokens based on storage type
const getStorage = () => {
  // Check if we should use sessionStorage (not remember me)
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

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const storage = getStorage();
    const token = storage.getItem('accessToken');
    if (token) {
      loadProfile();
    } else {
      setLoading(false);
    }
  }, []);

  const loadProfile = async () => {
    try {
      const response = await userApi.getProfile();
      setUser(response.data);
    } catch (error) {
      clearAllTokens();
    } finally {
      setLoading(false);
    }
  };

  const login = async (email, password, rememberMe = false) => {
    const response = await authApi.login(email, password);
    const data = response.data;

    // Check if 2FA is required
    if (data.twoFactorRequired) {
      // Don't save tokens yet, return to let UI handle 2FA
      return {
        twoFactorRequired: true,
        token: data.token,
        rememberMe
      };
    }

    // Normal login - save tokens
    const storage = rememberMe ? localStorage : sessionStorage;
    storage.setItem('accessToken', data.token.accessToken);
    storage.setItem('refreshToken', data.token.refreshToken);
    await loadProfile();
    return data;
  };

  const complete2FALogin = async (loginData) => {
    // Called after successful 2FA verification
    const { token, rememberMe } = loginData;
    const storage = rememberMe ? localStorage : sessionStorage;
    storage.setItem('accessToken', token.accessToken);
    storage.setItem('refreshToken', token.refreshToken);
    await loadProfile();
  };

  const signup = async (data) => {
    const response = await authApi.signup(data);
    const { token, ...userData } = response.data;
    localStorage.setItem('accessToken', token.accessToken);
    localStorage.setItem('refreshToken', token.refreshToken);
    await loadProfile();
    return response.data;
  };

  const handleOAuth2Callback = async (accessToken, refreshToken, rememberMe = true) => {
    const storage = rememberMe ? localStorage : sessionStorage;
    storage.setItem('accessToken', accessToken);
    storage.setItem('refreshToken', refreshToken);
    await loadProfile();
  };

  const logout = async () => {
    try {
      const storage = getStorage();
      const accessToken = storage.getItem('accessToken');
      const refreshToken = storage.getItem('refreshToken');
      await authApi.logout(accessToken, refreshToken);
    } catch (error) {
      // ignore logout errors
    } finally {
      clearAllTokens();
      setUser(null);
    }
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, signup, logout, handleOAuth2Callback, loadProfile, complete2FALogin }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
