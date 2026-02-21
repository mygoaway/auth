import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { twoFactorApi, userApi } from '../api/auth';

const OAUTH2_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export default function LoginPage() {
  const [showEmailLogin, setShowEmailLogin] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // 2FA states
  const [requires2FA, setRequires2FA] = useState(false);
  const [twoFactorCode, setTwoFactorCode] = useState('');
  const [tempLoginData, setTempLoginData] = useState(null);

  // Pending deletion states
  const [showPendingDeletionDialog, setShowPendingDeletionDialog] = useState(false);
  const [deletionRequestedAt, setDeletionRequestedAt] = useState(null);
  const [cancellingDeletion, setCancellingDeletion] = useState(false);

  const [passkeyLoading, setPasskeyLoading] = useState(false);

  const { login, complete2FALogin, passkeyLogin } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const result = await login(email, password, rememberMe);

      // Check if 2FA is required
      if (result.twoFactorRequired) {
        setRequires2FA(true);
        setTempLoginData(result);
        // Also check for pending deletion in 2FA flow
        if (result.pendingDeletion) {
          setDeletionRequestedAt(result.deletionRequestedAt);
        }
      } else if (result.pendingDeletion) {
        // Show pending deletion dialog
        setDeletionRequestedAt(result.deletionRequestedAt);
        setShowPendingDeletionDialog(true);
      } else {
        navigate('/dashboard');
      }
    } catch (err) {
      const message = err.response?.data?.error?.message
        || err.response?.data?.message
        || '로그인에 실패했습니다';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const handleCancelDeletion = async () => {
    setCancellingDeletion(true);
    try {
      await userApi.cancelDeletion();
      setShowPendingDeletionDialog(false);
      navigate('/dashboard');
    } catch (err) {
      const message = err.response?.data?.error?.message
        || err.response?.data?.message
        || '탈퇴 유예 취소에 실패했습니다';
      setError(message);
    } finally {
      setCancellingDeletion(false);
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' });
  };

  const calculateDaysLeft = (dateString) => {
    if (!dateString) return 30;
    const requestedDate = new Date(dateString);
    const deleteDate = new Date(requestedDate);
    deleteDate.setDate(deleteDate.getDate() + 30);
    const today = new Date();
    const daysLeft = Math.ceil((deleteDate - today) / (1000 * 60 * 60 * 24));
    return Math.max(0, daysLeft);
  };

  const handle2FASubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await twoFactorApi.verify(twoFactorCode);
      if (complete2FALogin) {
        await complete2FALogin(tempLoginData);
      }
      navigate('/dashboard');
    } catch (err) {
      const message = err.response?.data?.error?.message
        || err.response?.data?.message
        || '인증 코드가 올바르지 않습니다';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const handlePasskeyLogin = async () => {
    if (!window.PublicKeyCredential) {
      setError('이 브라우저는 패스키를 지원하지 않습니다');
      return;
    }
    setError('');
    setPasskeyLoading(true);
    try {
      await passkeyLogin();
      navigate('/dashboard');
    } catch (err) {
      if (err.name === 'NotAllowedError') {
        setError('패스키 인증이 취소되었습니다');
      } else {
        const message = err.response?.data?.error?.message
          || err.response?.data?.message
          || '패스키 로그인에 실패했습니다';
        setError(message);
      }
    } finally {
      setPasskeyLoading(false);
    }
  };

  const handleSocialLogin = (provider) => {
    window.location.href = `${OAUTH2_BASE_URL}/oauth2/authorization/${provider}`;
  };

  // 탈퇴 유예 상태 다이얼로그
  if (showPendingDeletionDialog) {
    const daysLeft = calculateDaysLeft(deletionRequestedAt);
    return (
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-logo">
            <h1>Authly</h1>
          </div>
          <div style={{ textAlign: 'center', marginBottom: '20px' }}>
            <div style={{ fontSize: '48px', marginBottom: '16px' }}>⚠️</div>
            <h2 style={{ marginBottom: '12px', color: '#e74c3c' }}>탈퇴 유예 상태</h2>
          </div>

          <div style={{
            background: '#fff3cd',
            border: '1px solid #ffc107',
            borderRadius: '8px',
            padding: '16px',
            marginBottom: '20px'
          }}>
            <p style={{ margin: 0, lineHeight: '1.6', color: '#856404' }}>
              해당 계정은 <strong>{formatDate(deletionRequestedAt)}</strong>에 탈퇴를 요청하여
              현재 <strong style={{ color: '#e74c3c' }}>탈퇴 유예 상태</strong>입니다.
            </p>
            <p style={{ margin: '12px 0 0 0', lineHeight: '1.6', color: '#856404' }}>
              <strong>{daysLeft}일</strong> 후에 계정이 영구 삭제됩니다.
            </p>
          </div>

          <p style={{ textAlign: 'center', color: '#666', marginBottom: '20px' }}>
            계속 서비스를 이용하시려면 탈퇴 유예를 취소해주세요.
          </p>

          {error && <div className="error-message">{error}</div>}

          <button
            className="btn btn-primary"
            onClick={handleCancelDeletion}
            disabled={cancellingDeletion}
          >
            {cancellingDeletion ? '처리 중...' : '탈퇴 유예 취소하고 계속 사용하기'}
          </button>
        </div>
      </div>
    );
  }

  // 로그인 방법 선택 화면 (넷마블 1번 스크린샷)
  if (!showEmailLogin) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-logo">
            <h1>Authly</h1>
          </div>
          <p className="auth-subtitle">로그인 방법을 선택해 주세요</p>

          {error && <div className="error-message">{error}</div>}

          {window.PublicKeyCredential && (
            <>
              <button
                className="btn btn-primary passkey-login-btn"
                onClick={handlePasskeyLogin}
                disabled={passkeyLoading}
              >
                <span className="passkey-icon">🔑</span>
                {passkeyLoading ? '인증 중...' : '패스키로 로그인'}
              </button>

              <div className="divider">
                <span>또는</span>
              </div>
            </>
          )}

          <div className="social-login-buttons">
            <button className="social-btn email" onClick={() => setShowEmailLogin(true)}>
              <span className="icon">✉</span>
              Email로 로그인
            </button>
            <button className="social-btn google" onClick={() => handleSocialLogin('google')}>
              <span className="icon">G</span>
              Google로 로그인
            </button>
            <button className="social-btn kakao" onClick={() => handleSocialLogin('kakao')}>
              <span className="icon">💬</span>
              카카오로 로그인
            </button>
            <button className="social-btn naver" onClick={() => handleSocialLogin('naver')}>
              <span className="icon">N</span>
              네이버로 로그인
            </button>
          </div>

          <div className="divider">
            <span>또는</span>
          </div>

          <div className="auth-links">
            <Link to="/signup">회원가입</Link>
          </div>
        </div>
      </div>
    );
  }

  // 2FA 인증 화면
  if (requires2FA) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-logo">
            <h1>Authly</h1>
          </div>
          <p className="auth-subtitle">2단계 인증</p>
          <p className="auth-description">인증 앱에서 생성된 6자리 코드를 입력하세요</p>

          {error && <div className="error-message">{error}</div>}

          <form onSubmit={handle2FASubmit}>
            <div className="form-group">
              <div className="input-wrapper">
                <input
                  type="text"
                  value={twoFactorCode}
                  onChange={(e) => setTwoFactorCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="000000"
                  maxLength={6}
                  autoFocus
                  style={{ textAlign: 'center', fontSize: '24px', letterSpacing: '8px' }}
                  required
                />
              </div>
            </div>

            <button type="submit" className="btn btn-primary" disabled={loading || twoFactorCode.length !== 6}>
              {loading ? '확인 중...' : '확인'}
            </button>
          </form>

          <div className="back-link" style={{ marginTop: '20px' }}>
            <a href="#" onClick={(e) => { e.preventDefault(); setRequires2FA(false); setTwoFactorCode(''); setError(''); }}>
              다른 방법으로 로그인
            </a>
          </div>
        </div>
      </div>
    );
  }

  // 이메일 로그인 화면
  return (
    <div className="auth-container">
      <div className="auth-card">
        <div className="auth-logo">
          <h1>Authly</h1>
        </div>

        {error && <div className="error-message">{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <div className="input-wrapper">
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="이메일을 입력하세요"
                required
              />
              {email && (
                <span className="input-icon" onClick={() => setEmail('')}>✕</span>
              )}
            </div>
          </div>

          <div className="form-group">
            <div className="input-wrapper">
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="비밀번호를 입력하세요"
                required
              />
              <span
                className="input-icon"
                onClick={() => setShowPassword(!showPassword)}
              >
                {showPassword ? '🙈' : '👁'}
              </span>
            </div>
          </div>

          <div className="form-group checkbox-group">
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
              />
              <span className="checkbox-text">로그인 상태 유지</span>
            </label>
          </div>

          <button type="submit" className="btn btn-primary" disabled={loading}>
            {loading ? '로그인 중...' : 'Email로 로그인'}
          </button>
        </form>

        <div className="auth-links">
          <Link to="/signup">회원가입</Link>
          <span className="separator">|</span>
          <Link to="/forgot-password">비밀번호 찾기</Link>
        </div>

        <div className="divider">
          <span>또는</span>
        </div>

        <div className="back-link">
          <a href="#" onClick={(e) => { e.preventDefault(); setShowEmailLogin(false); }}>
            다른 계정으로 로그인
          </a>
        </div>
      </div>
    </div>
  );
}
