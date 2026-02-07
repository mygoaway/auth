import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { twoFactorApi } from '../api/auth';

const OAUTH2_BASE_URL = 'http://localhost:8080';

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

  const { login, complete2FALogin } = useAuth();
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

  const handleSocialLogin = (provider) => {
    window.location.href = `${OAUTH2_BASE_URL}/oauth2/authorization/${provider}`;
  };

  // 로그인 방법 선택 화면 (넷마블 1번 스크린샷)
  if (!showEmailLogin) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-logo">
            <h1>authservice</h1>
          </div>
          <p className="auth-subtitle">로그인 방법을 선택해 주세요</p>

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
            <button className="social-btn facebook" onClick={() => handleSocialLogin('facebook')}>
              <span className="icon">f</span>
              Facebook으로 로그인
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
            <h1>authservice</h1>
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
          <h1>authservice</h1>
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
