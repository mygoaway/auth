import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const OAUTH2_BASE_URL = 'http://localhost:8080';

export default function LoginPage() {
  const [showEmailLogin, setShowEmailLogin] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await login(email, password);
      navigate('/dashboard');
    } catch (err) {
      const message = err.response?.data?.error?.message
        || err.response?.data?.message
        || '로그인에 실패했습니다';
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

  // 이메일 로그인 화면 (넷마블 2번 스크린샷)
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
