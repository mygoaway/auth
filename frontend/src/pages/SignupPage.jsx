import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { authApi } from '../api/auth';

const OAUTH2_BASE_URL = 'http://localhost:8080';

export default function SignupPage() {
  const [step, setStep] = useState(1); // 1: method selection, 2: email input, 3: verification, 4: complete signup
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [tokenId, setTokenId] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [nickname, setNickname] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const { signup } = useAuth();
  const navigate = useNavigate();

  const handleSocialSignup = (provider) => {
    window.location.href = `${OAUTH2_BASE_URL}/oauth2/authorization/${provider}`;
  };

  const handleSendVerification = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await authApi.sendVerification(email);
      setTokenId(response.data.tokenId);
      setSuccess('인증 코드가 이메일로 전송되었습니다');
      setStep(3);
    } catch (err) {
      const message = err.response?.data?.error?.message
        || err.response?.data?.message
        || '인증 코드 전송에 실패했습니다';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyCode = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await authApi.verifyCode(email, code);
      setSuccess('이메일 인증이 완료되었습니다');
      setStep(4);
    } catch (err) {
      const message = err.response?.data?.error?.message
        || err.response?.data?.message
        || '인증 코드가 올바르지 않습니다';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const handleSignup = async (e) => {
    e.preventDefault();
    setError('');

    if (password !== passwordConfirm) {
      setError('비밀번호가 일치하지 않습니다');
      return;
    }

    if (password.length < 8) {
      setError('비밀번호는 8자 이상이어야 합니다');
      return;
    }

    setLoading(true);

    try {
      await signup({ tokenId, email, password, nickname });
      navigate('/dashboard');
    } catch (err) {
      const message = err.response?.data?.error?.message
        || err.response?.data?.message
        || '회원가입에 실패했습니다';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  // 회원가입 방법 선택 화면
  if (step === 1) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-logo">
            <h1>authservice</h1>
          </div>
          <p className="auth-subtitle">회원가입 방법을 선택해 주세요</p>

          <div className="social-login-buttons">
            <button className="social-btn email" onClick={() => setStep(2)}>
              <span className="icon">✉</span>
              Email로 회원가입
            </button>
            <button className="social-btn google" onClick={() => handleSocialSignup('google')}>
              <span className="icon">G</span>
              Google로 회원가입
            </button>
            <button className="social-btn kakao" onClick={() => handleSocialSignup('kakao')}>
              <span className="icon">💬</span>
              카카오로 회원가입
            </button>
            <button className="social-btn naver" onClick={() => handleSocialSignup('naver')}>
              <span className="icon">N</span>
              네이버로 회원가입
            </button>
            <button className="social-btn facebook" onClick={() => handleSocialSignup('facebook')}>
              <span className="icon">f</span>
              Facebook으로 회원가입
            </button>
          </div>

          <div className="divider">
            <span>또는</span>
          </div>

          <div className="auth-links">
            <Link to="/login">이미 계정이 있으신가요? 로그인</Link>
          </div>
        </div>
      </div>
    );
  }

  // 이메일 입력 화면
  if (step === 2) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-logo">
            <h1>authservice</h1>
          </div>
          <p className="auth-subtitle">이메일을 입력해 주세요</p>

          {error && <div className="error-message">{error}</div>}

          <form onSubmit={handleSendVerification}>
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

            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? '전송 중...' : '인증 코드 전송'}
            </button>
          </form>

          <div className="back-link">
            <a href="#" onClick={(e) => { e.preventDefault(); setStep(1); setError(''); }}>
              다른 방법으로 회원가입
            </a>
          </div>
        </div>
      </div>
    );
  }

  // 인증 코드 입력 화면
  if (step === 3) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-logo">
            <h1>authservice</h1>
          </div>
          <p className="auth-subtitle">인증 코드를 입력해 주세요</p>

          {error && <div className="error-message">{error}</div>}
          {success && <div className="success-message">{success}</div>}

          <form onSubmit={handleVerifyCode}>
            <div className="form-group">
              <label>이메일</label>
              <input type="email" value={email} disabled className="disabled-input" />
            </div>

            <div className="form-group">
              <label>인증 코드 (6자리)</label>
              <div className="input-wrapper">
                <input
                  type="text"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  placeholder="인증 코드를 입력하세요"
                  maxLength={6}
                  required
                />
                {code && (
                  <span className="input-icon" onClick={() => setCode('')}>✕</span>
                )}
              </div>
            </div>

            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? '확인 중...' : '인증 확인'}
            </button>
          </form>

          <div className="back-link">
            <a href="#" onClick={(e) => { e.preventDefault(); setStep(2); setError(''); setSuccess(''); }}>
              이메일 다시 입력
            </a>
          </div>
        </div>
      </div>
    );
  }

  // 회원정보 입력 화면
  return (
    <div className="auth-container">
      <div className="auth-card">
        <div className="auth-logo">
          <h1>authservice</h1>
        </div>
        <p className="auth-subtitle">회원 정보를 입력해 주세요</p>

        {error && <div className="error-message">{error}</div>}
        {success && <div className="success-message">{success}</div>}

        <form onSubmit={handleSignup}>
          <div className="form-group">
            <label>이메일</label>
            <input type="email" value={email} disabled className="disabled-input" />
            <span className="verified-badge">✓ 인증완료</span>
          </div>

          <div className="form-group">
            <label>닉네임</label>
            <div className="input-wrapper">
              <input
                type="text"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                placeholder="닉네임 (2~20자)"
                minLength={2}
                maxLength={20}
                required
              />
              {nickname && (
                <span className="input-icon" onClick={() => setNickname('')}>✕</span>
              )}
            </div>
          </div>

          <div className="form-group">
            <label>비밀번호</label>
            <div className="input-wrapper">
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="영문, 숫자, 특수문자 포함 8자 이상"
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

          <div className="form-group">
            <label>비밀번호 확인</label>
            <div className="input-wrapper">
              <input
                type={showPasswordConfirm ? 'text' : 'password'}
                value={passwordConfirm}
                onChange={(e) => setPasswordConfirm(e.target.value)}
                placeholder="비밀번호를 다시 입력하세요"
                required
              />
              <span
                className="input-icon"
                onClick={() => setShowPasswordConfirm(!showPasswordConfirm)}
              >
                {showPasswordConfirm ? '🙈' : '👁'}
              </span>
            </div>
          </div>

          <button type="submit" className="btn btn-primary" disabled={loading}>
            {loading ? '가입 중...' : '회원가입'}
          </button>
        </form>

        <div className="auth-links">
          <Link to="/login">이미 계정이 있으신가요? 로그인</Link>
        </div>
      </div>
    </div>
  );
}
