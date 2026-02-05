import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { userApi, authApi } from '../api/auth';

export default function DashboardPage() {
  const { user, logout, loadProfile } = useAuth();
  const navigate = useNavigate();

  const [editModal, setEditModal] = useState(null); // 'nickname' | 'phone' | 'recoveryEmail' | 'password'
  const [editValue, setEditValue] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const openEdit = (type, currentValue) => {
    setEditModal(type);
    setEditValue(currentValue || '');
    setCurrentPassword('');
    setNewPassword('');
    setError('');
    setSuccess('');
  };

  const handleEdit = async () => {
    setError('');
    setLoading(true);

    try {
      switch (editModal) {
        case 'nickname':
          await userApi.updateNickname(editValue);
          break;
        case 'phone':
          await userApi.updatePhone(editValue);
          break;
        case 'recoveryEmail':
          await userApi.updateRecoveryEmail(editValue);
          break;
        case 'password':
          await authApi.changePassword(currentPassword, newPassword);
          break;
      }

      setSuccess('변경되었습니다');
      await loadProfile();
      setTimeout(() => {
        setEditModal(null);
        setSuccess('');
      }, 1000);
    } catch (err) {
      const message = err.response?.data?.error?.message
        || err.response?.data?.message
        || '변경에 실패했습니다';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  if (!user) return null;

  return (
    <div>
      <nav className="navbar">
        <div className="container" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span className="navbar-brand">Auth Service</span>
          <button className="btn btn-secondary btn-small" onClick={handleLogout}>
            로그아웃
          </button>
        </div>
      </nav>

      <div className="dashboard">
        <div className="dashboard-header">
          <h1>마이페이지</h1>
        </div>

        <div className="profile-card">
          <h2>기본 정보</h2>

          <div className="profile-row">
            <span className="profile-label">UUID</span>
            <span className="profile-value" style={{ fontSize: '12px', color: '#999' }}>
              {user.userUuid}
            </span>
          </div>

          <div className="profile-row">
            <span className="profile-label">이메일</span>
            <span className="profile-value">{user.email || '-'}</span>
          </div>

          <div className="profile-row">
            <span className="profile-label">닉네임</span>
            <span className="profile-value">
              {user.nickname || '-'}
              <button className="edit-btn" onClick={() => openEdit('nickname', user.nickname)}>
                변경
              </button>
            </span>
          </div>

          <div className="profile-row">
            <span className="profile-label">핸드폰 번호</span>
            <span className="profile-value">
              {user.phone || '미등록'}
              <button className="edit-btn" onClick={() => openEdit('phone', user.phone)}>
                {user.phone ? '변경' : '등록'}
              </button>
            </span>
          </div>

          <div className="profile-row">
            <span className="profile-label">복구 이메일</span>
            <span className="profile-value">
              {user.recoveryEmail || '미등록'}
              <button className="edit-btn" onClick={() => openEdit('recoveryEmail', user.recoveryEmail)}>
                {user.recoveryEmail ? '변경' : '등록'}
              </button>
            </span>
          </div>

          <div className="profile-row">
            <span className="profile-label">상태</span>
            <span className="profile-value">{user.status}</span>
          </div>
        </div>

        <div className="profile-card">
          <h2>연결된 계정</h2>
          <div className="channel-list" style={{ padding: '8px 0' }}>
            {user.channels?.map((channel, index) => (
              <span key={index} className={`channel-badge ${channel.channelCode}`}>
                {channel.channelCode}
              </span>
            ))}
          </div>
        </div>

        <div className="profile-card">
          <h2>보안</h2>
          <div className="profile-row">
            <span className="profile-label">비밀번호</span>
            <span className="profile-value">
              <button className="edit-btn" onClick={() => openEdit('password')}>
                변경
              </button>
            </span>
          </div>
        </div>
      </div>

      {/* Edit Modal */}
      {editModal && (
        <div className="modal-overlay" onClick={() => setEditModal(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h2>
              {editModal === 'nickname' && '닉네임 변경'}
              {editModal === 'phone' && '핸드폰 번호 변경'}
              {editModal === 'recoveryEmail' && '복구 이메일 변경'}
              {editModal === 'password' && '비밀번호 변경'}
            </h2>

            {error && <div className="error-message">{error}</div>}
            {success && <div className="success-message">{success}</div>}

            {editModal === 'password' ? (
              <>
                <div className="form-group">
                  <label>현재 비밀번호</label>
                  <input
                    type="password"
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    placeholder="현재 비밀번호"
                  />
                </div>
                <div className="form-group">
                  <label>새 비밀번호</label>
                  <input
                    type="password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="영문, 숫자, 특수문자 포함 8자 이상"
                  />
                </div>
              </>
            ) : (
              <div className="form-group">
                <label>
                  {editModal === 'nickname' && '새 닉네임'}
                  {editModal === 'phone' && '핸드폰 번호'}
                  {editModal === 'recoveryEmail' && '복구 이메일'}
                </label>
                <input
                  type={editModal === 'recoveryEmail' ? 'email' : 'text'}
                  value={editValue}
                  onChange={(e) => setEditValue(e.target.value)}
                  placeholder={
                    editModal === 'nickname' ? '새 닉네임 (2~20자)' :
                    editModal === 'phone' ? '010-1234-5678' :
                    '복구 이메일 주소'
                  }
                />
              </div>
            )}

            <div className="modal-actions">
              <button className="btn btn-secondary" onClick={() => setEditModal(null)}>
                취소
              </button>
              <button className="btn btn-primary" onClick={handleEdit} disabled={loading}>
                {loading ? '변경 중...' : '변경'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
