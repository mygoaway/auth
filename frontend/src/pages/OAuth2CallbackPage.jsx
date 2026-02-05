import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

export default function OAuth2CallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { handleOAuth2Callback } = useAuth();
  const [error, setError] = useState('');

  useEffect(() => {
    const accessToken = searchParams.get('accessToken');
    const refreshToken = searchParams.get('refreshToken');
    const errorParam = searchParams.get('error');

    if (errorParam) {
      setError(decodeURIComponent(errorParam));
      setTimeout(() => navigate('/login'), 3000);
      return;
    }

    if (accessToken && refreshToken) {
      handleOAuth2Callback(accessToken, refreshToken).then(() => {
        navigate('/dashboard');
      });
    } else {
      setError('소셜 로그인에 실패했습니다');
      setTimeout(() => navigate('/login'), 3000);
    }
  }, [searchParams, navigate, handleOAuth2Callback]);

  if (error) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <h1>로그인 실패</h1>
          <div className="error-message">{error}</div>
          <p style={{ textAlign: 'center', marginTop: '16px', color: '#666' }}>
            잠시 후 로그인 페이지로 이동합니다...
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-container">
      <div className="auth-card" style={{ textAlign: 'center' }}>
        <h1>로그인 처리 중...</h1>
        <p style={{ color: '#666', marginTop: '16px' }}>잠시만 기다려주세요</p>
      </div>
    </div>
  );
}
