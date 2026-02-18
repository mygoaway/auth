import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { authApi, userApi, passkeyApi } from '../api/auth';

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
    } catch {
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
    } catch {
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
    const { token } = response.data;
    localStorage.setItem('accessToken', token.accessToken);
    localStorage.setItem('refreshToken', token.refreshToken);
    await loadProfile();
    return response.data;
  }, [loadProfile]);

  const passkeyLogin = useCallback(async () => {
    // 1. Get authentication options from server
    const optionsRes = await passkeyApi.getAuthenticationOptions();
    const options = optionsRes.data;

    // 2. Call WebAuthn API
    const challengeBuffer = Uint8Array.from(atob(options.challenge.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));

    const publicKeyOptions = {
      challenge: challengeBuffer,
      timeout: options.timeout,
      rpId: options.rpId,
      userVerification: options.userVerification || 'preferred',
    };

    if (options.allowCredentials && options.allowCredentials.length > 0) {
      publicKeyOptions.allowCredentials = options.allowCredentials.map(cred => ({
        id: Uint8Array.from(atob(cred.id.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0)),
        type: cred.type,
        transports: cred.transports,
      }));
    }

    const credential = await navigator.credentials.get({ publicKey: publicKeyOptions });

    // 3. Send assertion to server
    const arrayBufferToBase64Url = (buffer) => {
      const bytes = new Uint8Array(buffer);
      let str = '';
      bytes.forEach(b => str += String.fromCharCode(b));
      return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
    };

    const verifyRes = await passkeyApi.verifyAuthentication({
      credentialId: arrayBufferToBase64Url(credential.rawId),
      authenticatorData: arrayBufferToBase64Url(credential.response.authenticatorData),
      clientDataJSON: arrayBufferToBase64Url(credential.response.clientDataJSON),
      signature: arrayBufferToBase64Url(credential.response.signature),
      userHandle: credential.response.userHandle ? arrayBufferToBase64Url(credential.response.userHandle) : null,
    });

    const data = verifyRes.data;
    localStorage.setItem('accessToken', data.token.accessToken);
    localStorage.setItem('refreshToken', data.token.refreshToken);
    await loadProfile();
    return data;
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
    } catch {
      // ignore logout errors
    } finally {
      clearAllTokens();
      setUser(null);
      profileLoaded.current = false;
    }
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, login, signup, logout, handleOAuth2Callback, loadProfile: refreshProfile, complete2FALogin, passkeyLogin }}>
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
