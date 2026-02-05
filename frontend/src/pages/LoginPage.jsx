import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const OAUTH2_BASE_URL = 'http://localhost:8080';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
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

  return (
    <div className="auth-container">
      <div className="auth-card">
        <h1>로그인</h1>

        {error && <div className="error-message">{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>이메일</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="이메일을 입력하세요"
              required
            />
          </div>

          <div className="form-group">
            <label>비밀번호</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="비밀번호를 입력하세요"
              required
            />
          </div>

          <button type="submit" className="btn btn-primary" disabled={loading}>
            {loading ? '로그인 중...' : '로그인'}
          </button>
        </form>

        <div className="auth-link">
          <Link to="/forgot-password">비밀번호를 잊으셨나요?</Link>
        </div>

        <div className="social-login-divider">
          <span>또는</span>
        </div>

        <div className="social-login">
          <button className="social-btn google" onClick={() => handleSocialLogin('google')}>
            Google로 로그인
          </button>
          <button className="social-btn kakao" onClick={() => handleSocialLogin('kakao')}>
            카카오로 로그인
          </button>
          <button className="social-btn naver" onClick={() => handleSocialLogin('naver')}>
            네이버로 로그인
          </button>
          <button className="social-btn facebook" onClick={() => handleSocialLogin('facebook')}>
            Facebook으로 로그인
          </button>
        </div>

        <div className="auth-link" style={{ marginTop: '24px' }}>
          계정이 없으신가요? <Link to="/signup">회원가입</Link>
        </div>
      </div>
    </div>
  );
}
