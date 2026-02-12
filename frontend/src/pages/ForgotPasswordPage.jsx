import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth';
import PasswordStrengthMeter from '../components/PasswordStrengthMeter';

export default function ForgotPasswordPage() {
  const [step, setStep] = useState(1); // 1: recovery email, 2: verify, 3: select account, 4: new password
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [tokenId, setTokenId] = useState('');
  const [accounts, setAccounts] = useState([]);
  const [selectedLoginEmail, setSelectedLoginEmail] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSendVerification = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await authApi.sendPasswordResetVerification(email);
      setTokenId(response.data.tokenId);
      setSuccess('인증 코드가 이메일로 전송되었습니다');
      setStep(2);
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
      await authApi.verifyCode(email, code, 'PASSWORD_RESET');
      setSuccess('');

      // 인증 완료 후 연결된 계정 목록 조회
      const accountsResponse = await authApi.getRecoveryAccounts(tokenId, email);
      const accountList = accountsResponse.data.accounts;
      setAccounts(accountList);

      if (accountList.length === 1) {
        // 계정이 하나뿐이면 자동 선택 후 바로 비밀번호 변경 단계로
        setSelectedLoginEmail(accountList[0].loginEmail);
        setStep(4);
      } else {
        setStep(3);
      }
    } catch (err) {
      const message = err.response?.data?.error?.message
        || err.response?.data?.message
        || '인증 코드가 올바르지 않습니다';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const handleSelectAccount = (loginEmail) => {
    setSelectedLoginEmail(loginEmail);
    setError('');
    setSuccess('');
    setStep(4);
  };

  const handleResetPassword = async (e) => {
    e.preventDefault();
    setError('');

    if (newPassword !== passwordConfirm) {
      setError('비밀번호가 일치하지 않습니다');
      return;
    }

    if (newPassword.length < 8) {
      setError('비밀번호는 8자 이상이어야 합니다');
      return;
    }

    setLoading(true);

    try {
      await authApi.resetPassword(tokenId, email, selectedLoginEmail, newPassword);
      setSuccess('비밀번호가 재설정되었습니다');
      setTimeout(() => navigate('/login'), 2000);
    } catch (err) {
      const message = err.response?.data?.error?.message
        || err.response?.data?.message
        || '비밀번호 재설정에 실패했습니다';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  // Step 1: 복구 이메일 입력 화면
  if (step === 1) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-logo">
            <h1>Authly</h1>
          </div>
          <p className="auth-subtitle">비밀번호 재설정</p>
          <p className="auth-description">
            복구 이메일로 인증 코드를 전송합니다
          </p>

          {error && <div className="error-message">{error}</div>}

          <form onSubmit={handleSendVerification}>
            <div className="form-group">
              <div className="input-wrapper">
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="복구 이메일을 입력하세요"
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

          <div className="auth-links">
            <Link to="/login">로그인으로 돌아가기</Link>
          </div>
        </div>
      </div>
    );
  }

  // Step 2: 인증 코드 입력 화면
  if (step === 2) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-logo">
            <h1>Authly</h1>
          </div>
          <p className="auth-subtitle">인증 코드 입력</p>

          {error && <div className="error-message">{error}</div>}
          {success && <div className="success-message">{success}</div>}

          <form onSubmit={handleVerifyCode}>
            <div className="form-group">
              <label>복구 이메일</label>
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
            <a href="#" onClick={(e) => { e.preventDefault(); setStep(1); setError(''); setSuccess(''); }}>
              복구 이메일 다시 입력
            </a>
          </div>
        </div>
      </div>
    );
  }

  // Step 3: 계정 선택 화면
  if (step === 3) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-logo">
            <h1>Authly</h1>
          </div>
          <p className="auth-subtitle">계정 선택</p>
          <p className="auth-description">
            비밀번호를 재설정할 계정을 선택하세요
          </p>

          {error && <div className="error-message">{error}</div>}

          <div className="account-list">
            {accounts.map((account, index) => (
              <div
                key={index}
                className="account-item"
                onClick={() => handleSelectAccount(account.loginEmail)}
              >
                <div className="account-email">{account.maskedEmail}</div>
                <span className="account-arrow">&rsaquo;</span>
              </div>
            ))}
          </div>

          <div className="back-link">
            <a href="#" onClick={(e) => { e.preventDefault(); setStep(1); setError(''); setSuccess(''); }}>
              복구 이메일 다시 입력
            </a>
          </div>
        </div>
      </div>
    );
  }

  // Step 4: 새 비밀번호 입력 화면
  return (
    <div className="auth-container">
      <div className="auth-card">
        <div className="auth-logo">
          <h1>Authly</h1>
        </div>
        <p className="auth-subtitle">새 비밀번호 설정</p>

        {error && <div className="error-message">{error}</div>}
        {success && <div className="success-message">{success}</div>}

        <form onSubmit={handleResetPassword}>
          <div className="form-group">
            <label>복구 이메일</label>
            <input type="email" value={email} disabled className="disabled-input" />
            <span className="verified-badge">✓ 인증완료</span>
          </div>

          <div className="form-group">
            <label>로그인 이메일</label>
            <input type="email" value={selectedLoginEmail} disabled className="disabled-input" />
          </div>

          <div className="form-group">
            <label>새 비밀번호</label>
            <div className="input-wrapper">
              <input
                type={showPassword ? 'text' : 'password'}
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
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
            <PasswordStrengthMeter password={newPassword} />
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
            {loading ? '변경 중...' : '비밀번호 변경'}
          </button>
        </form>

        <div className="auth-links">
          <Link to="/login">로그인으로 돌아가기</Link>
        </div>
      </div>
    </div>
  );
}
