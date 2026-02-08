import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { userApi } from '../api/auth';

export default function OAuth2CallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { handleOAuth2Callback } = useAuth();
  const [error, setError] = useState('');
  const [showPendingDeletionDialog, setShowPendingDeletionDialog] = useState(false);
  const [deletionRequestedAt, setDeletionRequestedAt] = useState(null);
  const [cancellingDeletion, setCancellingDeletion] = useState(false);

  useEffect(() => {
    const accessToken = searchParams.get('accessToken');
    const refreshToken = searchParams.get('refreshToken');
    const errorParam = searchParams.get('error');
    const pendingDeletion = searchParams.get('pendingDeletion');
    const deletionDate = searchParams.get('deletionRequestedAt');

    if (errorParam) {
      setError(decodeURIComponent(errorParam));
      setTimeout(() => navigate('/login'), 3000);
      return;
    }

    if (accessToken && refreshToken) {
      handleOAuth2Callback(accessToken, refreshToken).then(() => {
        if (pendingDeletion === 'true') {
          setDeletionRequestedAt(deletionDate);
          setShowPendingDeletionDialog(true);
        } else {
          navigate('/dashboard');
        }
      });
    } else {
      setError('소셜 로그인에 실패했습니다');
      setTimeout(() => navigate('/login'), 3000);
    }
  }, [searchParams, navigate, handleOAuth2Callback]);

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

  // 탈퇴 유예 상태 다이얼로그
  if (showPendingDeletionDialog) {
    const daysLeft = calculateDaysLeft(deletionRequestedAt);
    return (
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-logo">
            <h1>authservice</h1>
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
