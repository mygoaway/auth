import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const CHANNEL_NAMES = {
  GOOGLE: 'Google',
  KAKAO: '카카오',
  NAVER: '네이버',
  FACEBOOK: 'Facebook',
};

export default function OAuth2LinkCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { loadProfile } = useAuth();
  const [status, setStatus] = useState('processing');
  const [message, setMessage] = useState('계정 연동 처리 중...');

  useEffect(() => {
    const handleCallback = async () => {
      const success = searchParams.get('success');
      const channelCode = searchParams.get('channelCode');
      const error = searchParams.get('error');

      if (error) {
        setStatus('error');
        setMessage(decodeURIComponent(error));
        setTimeout(() => navigate('/dashboard'), 3000);
        return;
      }

      if (success === 'true' && channelCode) {
        setStatus('success');
        setMessage(`${CHANNEL_NAMES[channelCode] || channelCode} 계정이 연동되었습니다!`);

        // Refresh user profile to get updated channels
        try {
          await loadProfile();
        } catch (err) {
          console.error('Failed to refresh profile:', err);
        }

        setTimeout(() => navigate('/dashboard'), 2000);
      } else {
        setStatus('error');
        setMessage('계정 연동에 실패했습니다');
        setTimeout(() => navigate('/dashboard'), 3000);
      }
    };

    handleCallback();
  }, [searchParams, navigate, loadProfile]);

  return (
    <div className="auth-container">
      <div className="auth-card" style={{ textAlign: 'center' }}>
        <div className="auth-logo">
          <h1>authservice</h1>
        </div>

        {status === 'processing' && (
          <div className="callback-status">
            <div className="spinner"></div>
            <p>{message}</p>
          </div>
        )}

        {status === 'success' && (
          <div className="callback-status success">
            <div className="callback-icon">✓</div>
            <h2>연동 완료!</h2>
            <p>{message}</p>
            <p className="redirect-notice">잠시 후 대시보드로 이동합니다...</p>
          </div>
        )}

        {status === 'error' && (
          <div className="callback-status error">
            <div className="callback-icon">✕</div>
            <h2>연동 실패</h2>
            <p>{message}</p>
            <p className="redirect-notice">잠시 후 대시보드로 이동합니다...</p>
          </div>
        )}
      </div>
    </div>
  );
}
