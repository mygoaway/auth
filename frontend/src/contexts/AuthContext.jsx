import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
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
  const profileLoaded = useRef(false);

  const loadProfile = useCallback(async () => {
    if (profileLoaded.current) return;
    profileLoaded.current = true;
    try {
      const response = await userApi.getProfile();
      setUser(response.data);
    } catch (error) {
      profileLoaded.current = false;
      clearAllTokens();
    } finally {
      setLoading(false);
    }
  }, []);

  // 프로필 갱신이 필요한 경우 (닉네임 변경 등)
  const refreshProfile = useCallback(async () => {
    try {
      const response = await userApi.getProfile();
      setUser(response.data);
    } catch (error) {
      clearAllTokens();
    }
  }, []);

  useEffect(() => {
    const storage = getStorage();
    const token = storage.getItem('accessToken');
    if (token) {
      loadProfile();
    } else {
      setLoading(false);
    }
  }, [loadProfile]);

  const login = useCallback(async (email, password, rememberMe = false) => {
    const response = await authApi.login(email, password);
    const data = response.data;

    // Check if 2FA is required
    if (data.twoFactorRequired) {
      // Don't save tokens yet, return to let UI handle 2FA
      return {
        twoFactorRequired: true,
        token: data.token,
        rememberMe,
        pendingDeletion: data.pendingDeletion,
        deletionRequestedAt: data.deletionRequestedAt
      };
    }

    // Normal login - save tokens
    const storage = rememberMe ? localStorage : sessionStorage;
    storage.setItem('accessToken', data.token.accessToken);
    storage.setItem('refreshToken', data.token.refreshToken);
    await loadProfile();

    // Return pendingDeletion info along with data
    return {
      ...data,
      pendingDeletion: data.pendingDeletion,
      deletionRequestedAt: data.deletionRequestedAt
    };
  }, [loadProfile]);

  const complete2FALogin = useCallback(async (loginData) => {
    // Called after successful 2FA verification
    const { token, rememberMe } = loginData;
    const storage = rememberMe ? localStorage : sessionStorage;
    storage.setItem('accessToken', token.accessToken);
    storage.setItem('refreshToken', token.refreshToken);
    await loadProfile();
  }, [loadProfile]);

  const signup = useCallback(async (data) => {
    const response = await authApi.signup(data);
    const { token, ...userData } = response.data;
    localStorage.setItem('accessToken', token.accessToken);
    localStorage.setItem('refreshToken', token.refreshToken);
    await loadProfile();
    return response.data;
  }, [loadProfile]);

  const handleOAuth2Callback = useCallback(async (accessToken, refreshToken, rememberMe = true) => {
    const storage = rememberMe ? localStorage : sessionStorage;
    storage.setItem('accessToken', accessToken);
    storage.setItem('refreshToken', refreshToken);
    await loadProfile();
  }, [loadProfile]);

  const logout = useCallback(async () => {
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
      profileLoaded.current = false;
    }
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, login, signup, logout, handleOAuth2Callback, loadProfile: refreshProfile, complete2FALogin }}>
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
