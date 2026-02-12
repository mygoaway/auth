import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { userApi, authApi, phoneApi, emailApi, twoFactorApi, oauth2Api } from '../api/auth';
import PasswordStrengthMeter from '../components/PasswordStrengthMeter';

const OAUTH2_BASE_URL = 'http://localhost:8080';

const CHANNEL_INFO = {
  EMAIL: { name: 'Email', icon: '✉', color: '#6c757d' },
  GOOGLE: { name: 'Google', icon: 'G', color: '#DB4437' },
  KAKAO: { name: '카카오', icon: '💬', color: '#FEE500', textColor: '#000' },
  NAVER: { name: '네이버', icon: 'N', color: '#03C75A' },
};

export default function DashboardPage() {
  const { user, logout, loadProfile } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [activeTab, setActiveTab] = useState(() => {
    const tab = searchParams.get('tab');
    return ['home', 'profile', 'channels', 'security', 'activity'].includes(tab) ? tab : 'home';
  });
  const [channelsStatus, setChannelsStatus] = useState(null);
  const [loginHistory, setLoginHistory] = useState([]);
  const [twoFactorStatus, setTwoFactorStatus] = useState(null);
  const [twoFactorSetup, setTwoFactorSetup] = useState(null);
  const [twoFactorCode, setTwoFactorCode] = useState('');
  const [backupCodes, setBackupCodes] = useState([]);
  const [securityDashboard, setSecurityDashboard] = useState(null);
  const [weeklyActivity, setWeeklyActivity] = useState(null);
  const [trustedDevices, setTrustedDevices] = useState([]);
  const [suspiciousActivity, setSuspiciousActivity] = useState(null);
  const [securitySettings, setSecuritySettings] = useState(null);
  const [lastLogin, setLastLogin] = useState(null);
  const [passwordWarning, setPasswordWarning] = useState(null);
  const [modal, setModal] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Form states
  const [nickname, setNickname] = useState('');
  const [phone, setPhone] = useState('');
  const [phoneCode, setPhoneCode] = useState('');
  const [phoneTokenId, setPhoneTokenId] = useState('');
  const [phoneStep, setPhoneStep] = useState(1);
  const [recoveryEmail, setRecoveryEmail] = useState('');
  const [emailCode, setEmailCode] = useState('');
  const [emailTokenId, setEmailTokenId] = useState('');
  const [emailStep, setEmailStep] = useState(1);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('');
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState('');

  // Clear tab query param after reading it
  useEffect(() => {
    if (searchParams.get('tab')) {
      setSearchParams({}, { replace: true });
    }
  }, []);

  useEffect(() => {
    if (activeTab === 'home') {
      loadLastLogin();
      loadPasswordWarning();
    }
    if (activeTab === 'channels') {
      loadChannelsStatus();
    }
    if (activeTab === 'security') {
      loadLoginHistory();
      loadTwoFactorStatus();
      loadSecurityDashboard();
      loadTrustedDevices();
      loadSuspiciousActivity();
      loadSecuritySettings();
    }
    if (activeTab === 'activity') {
      loadWeeklyActivity();
    }
  }, [activeTab, user]);

  const loadLastLogin = async () => {
    try {
      const response = await userApi.getLoginHistory(1);
      if (response.data && response.data.length > 0) {
        setLastLogin(response.data[0]);
      }
    } catch (err) {
      console.error('Failed to load last login', err);
    }
  };

  const loadPasswordWarning = async () => {
    try {
      // 이메일 채널(비밀번호 로그인)이 있는 경우에만 비밀번호 경고 표시
      const hasEmail = user?.channels?.some(c => c.channelCode === 'EMAIL');
      if (!hasEmail) {
        setPasswordWarning(null);
        return;
      }

      const response = await userApi.getSecurityDashboard();
      const passwordFactor = response.data?.factors?.find(f => f.name === 'PASSWORD_HEALTH');
      if (passwordFactor && passwordFactor.score < 20) {
        setPasswordWarning({
          expired: passwordFactor.score < 10,
          message: passwordFactor.score < 10
            ? '비밀번호가 만료되었습니다. 새 비밀번호로 변경해주세요.'
            : '비밀번호 만료가 임박했습니다. 곧 변경이 필요합니다.'
        });
      } else {
        setPasswordWarning(null);
      }
    } catch (err) {
      console.error('Failed to load password warning', err);
    }
  };

  const loadChannelsStatus = async () => {
    try {
      const response = await userApi.getChannelsStatus();
      setChannelsStatus(response.data);
    } catch (err) {
      console.error('Failed to load channels status', err);
    }
  };

  const loadLoginHistory = async () => {
    try {
      const response = await userApi.getLoginHistory(10);
      setLoginHistory(response.data);
    } catch (err) {
      console.error('Failed to load login history', err);
    }
  };


  const handleRegenerateBackupCodes = async () => {
    if (!twoFactorCode || twoFactorCode.length !== 6) {
      setError('6자리 인증 코드를 입력해주세요');
      return;
    }
    try {
      setLoading(true);
      const response = await twoFactorApi.regenerateBackupCodes(twoFactorCode);
      setBackupCodes(response.data.backupCodes);
      setModal('2fa-backup');
    } catch (err) {
      setError(err.response?.data?.message || '백업 코드 재생성에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  const loadTwoFactorStatus = async () => {
    try {
      const response = await twoFactorApi.getStatus();
      setTwoFactorStatus(response.data);
    } catch (err) {
      console.error('Failed to load 2FA status', err);
    }
  };

  const loadSecurityDashboard = async () => {
    try {
      const response = await userApi.getSecurityDashboard();
      setSecurityDashboard(response.data);
    } catch (err) {
      console.error('Failed to load security dashboard', err);
    }
  };

  const loadWeeklyActivity = async () => {
    try {
      const response = await userApi.getWeeklyActivity();
      setWeeklyActivity(response.data);
    } catch (err) {
      console.error('Failed to load weekly activity', err);
    }
  };

  const loadSecuritySettings = async () => {
    try {
      const response = await userApi.getSecuritySettings();
      setSecuritySettings(response.data);
    } catch (err) {
      console.error('Failed to load security settings', err);
    }
  };

  const handleToggleLoginNotification = async () => {
    if (!securitySettings) return;
    try {
      const newVal = !securitySettings.loginNotificationEnabled;
      await userApi.updateLoginNotification(newVal);
      setSecuritySettings(prev => ({ ...prev, loginNotificationEnabled: newVal }));
    } catch (err) {
      setError('설정 변경에 실패했습니다');
    }
  };

  const handleToggleSuspiciousNotification = async () => {
    if (!securitySettings) return;
    try {
      const newVal = !securitySettings.suspiciousActivityNotificationEnabled;
      await userApi.updateSuspiciousNotification(newVal);
      setSecuritySettings(prev => ({ ...prev, suspiciousActivityNotificationEnabled: newVal }));
    } catch (err) {
      setError('설정 변경에 실패했습니다');
    }
  };

  const handleUnlockAccount = async () => {
    try {
      setLoading(true);
      await userApi.unlockAccount();
      setSuccess('계정 잠금이 해제되었습니다');
      await loadSecuritySettings();
      setTimeout(() => setSuccess(''), 2000);
    } catch (err) {
      setError(err.response?.data?.message || '잠금 해제에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  const loadSuspiciousActivity = async () => {
    try {
      const response = await userApi.getSuspiciousActivity();
      setSuspiciousActivity(response.data);
    } catch (err) {
      console.error('Failed to load suspicious activity', err);
    }
  };

  const loadTrustedDevices = async () => {
    try {
      const response = await userApi.getTrustedDevices();
      setTrustedDevices(response.data);
    } catch (err) {
      console.error('Failed to load trusted devices', err);
    }
  };

  const handleTrustCurrentDevice = async () => {
    try {
      setLoading(true);
      await userApi.trustCurrentDevice();
      setSuccess('현재 기기가 신뢰 기기로 등록되었습니다');
      await loadTrustedDevices();
      setTimeout(() => setSuccess(''), 2000);
    } catch (err) {
      setError(err.response?.data?.message || '기기 등록에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  const handleRemoveTrustedDevice = async (deviceId) => {
    if (!window.confirm('이 기기의 신뢰 등록을 해제하시겠습니까?')) return;
    try {
      setLoading(true);
      await userApi.removeTrustedDevice(deviceId);
      setSuccess('신뢰 기기가 해제되었습니다');
      await loadTrustedDevices();
      setTimeout(() => setSuccess(''), 2000);
    } catch (err) {
      setError(err.response?.data?.message || '기기 해제에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  const handleSetup2FA = async () => {
    try {
      setLoading(true);
      const response = await twoFactorApi.setup();
      setTwoFactorSetup(response.data);
      setModal('2fa-setup');
    } catch (err) {
      setError(err.response?.data?.message || '2FA 설정에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  const handleEnable2FA = async () => {
    if (!twoFactorCode || twoFactorCode.length !== 6) {
      setError('6자리 인증 코드를 입력해주세요');
      return;
    }
    try {
      setLoading(true);
      const response = await twoFactorApi.enable(twoFactorCode);
      setBackupCodes(response.data.backupCodes);
      setModal('2fa-backup');
      loadTwoFactorStatus();
    } catch (err) {
      setError(err.response?.data?.message || '2FA 활성화에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  const handleDisable2FA = async () => {
    if (!twoFactorCode || twoFactorCode.length !== 6) {
      setError('6자리 인증 코드를 입력해주세요');
      return;
    }
    try {
      setLoading(true);
      await twoFactorApi.disable(twoFactorCode);
      setSuccess('2단계 인증이 비활성화되었습니다');
      resetModal();
      loadTwoFactorStatus();
    } catch (err) {
      setError(err.response?.data?.message || '2FA 비활성화에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const resetModal = () => {
    setModal(null);
    setError('');
    setSuccess('');
    setNickname('');
    setPhone('');
    setPhoneCode('');
    setPhoneTokenId('');
    setPhoneStep(1);
    setRecoveryEmail('');
    setEmailCode('');
    setEmailTokenId('');
    setEmailStep(1);
    setCurrentPassword('');
    setNewPassword('');
    setNewPasswordConfirm('');
    setShowCurrentPassword(false);
    setShowNewPassword(false);
    setDeleteConfirm('');
    setTwoFactorCode('');
    setTwoFactorSetup(null);
    setBackupCodes([]);
  };

  const openModal = (type, initialValue = '') => {
    resetModal();
    setModal(type);
    if (type === 'nickname') setNickname(initialValue || '');
    if (type === 'phone') setPhone(initialValue || '');
    if (type === 'recoveryEmail') setRecoveryEmail(initialValue || '');
  };

  // Nickname update
  const handleUpdateNickname = async () => {
    setError('');
    setLoading(true);
    try {
      await userApi.updateNickname(nickname);
      setSuccess('닉네임이 변경되었습니다');
      await loadProfile();
      setTimeout(resetModal, 1500);
    } catch (err) {
      setError(err.response?.data?.error?.message || '닉네임 변경에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  // Phone verification
  const handleSendPhoneCode = async () => {
    setError('');

    // 기존 핸드폰 번호와 동일한지 검증
    const normalizedPhone = phone.replace(/[^0-9]/g, '');
    const normalizedUserPhone = user.phone?.replace(/[^0-9]/g, '');
    if (normalizedUserPhone && normalizedPhone === normalizedUserPhone) {
      setError('현재 등록된 핸드폰 번호와 동일합니다.');
      return;
    }

    setLoading(true);
    try {
      const response = await phoneApi.sendVerification(phone);
      setPhoneTokenId(response.data.tokenId);
      setPhoneStep(2);
      setSuccess('인증번호가 전송되었습니다');
    } catch (err) {
      setError(err.response?.data?.error?.message || '인증번호 전송에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyPhoneCode = async () => {
    setError('');
    setLoading(true);
    try {
      await phoneApi.verifyCode(phone, phoneCode);
      setPhoneStep(3);
      setSuccess('인증이 완료되었습니다');
    } catch (err) {
      setError(err.response?.data?.error?.message || '인증번호가 올바르지 않습니다');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdatePhone = async () => {
    setError('');
    setLoading(true);
    try {
      await userApi.updatePhone(phone, phoneTokenId);
      setSuccess('핸드폰 번호가 변경되었습니다');
      await loadProfile();
      loadSecurityDashboard();
      setTimeout(resetModal, 1500);
    } catch (err) {
      setError(err.response?.data?.error?.message || '핸드폰 번호 변경에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  // Recovery email verification
  const handleSendEmailCode = async () => {
    setError('');

    // 본인 이메일과 동일한지 검증
    if (user.email && recoveryEmail.toLowerCase() === user.email.toLowerCase()) {
      setError('복구 이메일은 현재 로그인 이메일과 다른 이메일을 사용해주세요.');
      return;
    }

    // 기존 복구 이메일과 동일한지 검증 (실제 등록된 경우에만)
    if (user.recoveryEmail && user.recoveryEmail.trim() !== '' && recoveryEmail.toLowerCase() === user.recoveryEmail.toLowerCase()) {
      setError('현재 등록된 복구 이메일과 동일합니다.');
      return;
    }

    setLoading(true);
    try {
      const response = await emailApi.sendVerification(recoveryEmail, 'EMAIL_CHANGE');
      setEmailTokenId(response.data.tokenId);
      setEmailStep(2);
      setSuccess('인증 코드가 전송되었습니다');
    } catch (err) {
      setError(err.response?.data?.error?.message || '인증 코드 전송에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyEmailCode = async () => {
    setError('');
    setLoading(true);
    try {
      await emailApi.verifyCode(recoveryEmail, emailCode, 'EMAIL_CHANGE');
      setEmailStep(3);
      setSuccess('인증이 완료되었습니다');
    } catch (err) {
      setError(err.response?.data?.error?.message || '인증 코드가 올바르지 않습니다');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdateRecoveryEmail = async () => {
    setError('');
    setLoading(true);
    try {
      await userApi.updateRecoveryEmail(recoveryEmail, emailTokenId);
      setSuccess('복구 이메일이 변경되었습니다');
      await loadProfile();
      loadSecurityDashboard();
      setTimeout(resetModal, 1500);
    } catch (err) {
      setError(err.response?.data?.error?.message || '복구 이메일 변경에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  // Password change
  const handleChangePassword = async () => {
    setError('');
    if (newPassword !== newPasswordConfirm) {
      setError('새 비밀번호가 일치하지 않습니다');
      return;
    }
    if (newPassword.length < 8) {
      setError('비밀번호는 8자 이상이어야 합니다');
      return;
    }
    setLoading(true);
    try {
      await authApi.changePassword(currentPassword, newPassword);
      setSuccess('비밀번호가 변경되었습니다. 다시 로그인해주세요.');
      setTimeout(() => {
        logout();
        navigate('/login');
      }, 2000);
    } catch (err) {
      setError(err.response?.data?.error?.message || '비밀번호 변경에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  // Account linking
  const handleLinkChannel = async (provider) => {
    try {
      setLoading(true);
      // First call API to prepare link state (this sends JWT token)
      const response = await oauth2Api.prepareLink(provider);
      const { authorizationUrl } = response.data;
      // Then redirect to OAuth2 authorization (no JWT needed, state is saved server-side)
      window.location.href = `${OAUTH2_BASE_URL}${authorizationUrl}`;
    } catch (err) {
      setError(err.response?.data?.message || '계정 연동 준비에 실패했습니다');
      setLoading(false);
    }
  };

  const handleUnlinkChannel = async (channelCode) => {
    if (!window.confirm(`${CHANNEL_INFO[channelCode]?.name || channelCode} 연동을 해제하시겠습니까?`)) {
      return;
    }
    setError('');
    setLoading(true);
    try {
      await userApi.unlinkChannel(channelCode);
      setSuccess(`${CHANNEL_INFO[channelCode]?.name || channelCode} 연동이 해제되었습니다`);
      await loadChannelsStatus();
      await loadProfile(); // 사용자 컨텍스트 업데이트
      setTimeout(() => setSuccess(''), 2000);
    } catch (err) {
      setError(err.response?.data?.error?.message || '연동 해제에 실패했습니다');
    } finally {
      setLoading(false);
    }
  };

  // Account deletion
  const handleDeleteAccount = async () => {
    if (deleteConfirm !== '회원탈퇴') {
      setError('확인 문구를 정확히 입력해주세요');
      return;
    }
    setError('');
    setLoading(true);
    try {
      await userApi.deleteAccount();
      await logout();
      navigate('/login');
    } catch (err) {
      setError(err.response?.data?.error?.message || '회원 탈퇴에 실패했습니다');
      setLoading(false);
    }
  };

  if (!user) return null;

  const hasEmailChannel = user.channels?.some(c => c.channelCode === 'EMAIL');

  return (
    <div className="dashboard-container">
      {/* Navbar */}
      <nav className="dashboard-navbar">
        <div className="navbar-content">
          <div className="navbar-brand">Authly</div>
          <button className="logout-btn" onClick={handleLogout}>
            로그아웃
          </button>
        </div>
      </nav>

      {/* Tabs */}
      <div className="dashboard-tabs">
        <button
          className={`tab-btn ${activeTab === 'home' ? 'active' : ''}`}
          onClick={() => setActiveTab('home')}
        >
          홈
        </button>
        <button
          className={`tab-btn ${activeTab === 'profile' ? 'active' : ''}`}
          onClick={() => setActiveTab('profile')}
        >
          내 정보
        </button>
        <button
          className={`tab-btn ${activeTab === 'channels' ? 'active' : ''}`}
          onClick={() => setActiveTab('channels')}
        >
          연동 계정
        </button>
        <button
          className={`tab-btn ${activeTab === 'security' ? 'active' : ''}`}
          onClick={() => setActiveTab('security')}
        >
          보안
        </button>
        <button
          className={`tab-btn ${activeTab === 'activity' ? 'active' : ''}`}
          onClick={() => setActiveTab('activity')}
        >
          활동
        </button>
      </div>

      {/* Content */}
      <div className="dashboard-content">
        {activeTab === 'home' && (
          <div className="tab-content">
            {/* Password Warning Banner */}
            {passwordWarning && (
              <div className={`warning-banner ${passwordWarning.expired ? 'expired' : 'warning'}`}>
                <span className="warning-banner-icon">{passwordWarning.expired ? '🔒' : '⚠️'}</span>
                <span className="warning-banner-text">{passwordWarning.message}</span>
                <button
                  className="warning-banner-btn"
                  onClick={() => { setActiveTab('security'); openModal('password'); }}
                >
                  비밀번호 변경
                </button>
              </div>
            )}

            <div className="welcome-section">
              <h2>안녕하세요, {user.nickname || '회원'}님!</h2>
              <p className="uuid-display">UUID: {user.userUuid}</p>
            </div>

            {/* Last Login Info */}
            {lastLogin && (
              <div className="info-card last-login-card">
                <h3>마지막 로그인</h3>
                <div className="last-login-info">
                  <div className="last-login-icon">
                    {lastLogin.deviceType === 'Mobile' ? '📱' : lastLogin.deviceType === 'Tablet' ? '📲' : '💻'}
                  </div>
                  <div className="last-login-details">
                    <div className="last-login-device">
                      {lastLogin.browser} / {lastLogin.os}
                      <span className={`login-status-badge ${lastLogin.isSuccess ? 'success' : 'failed'}`}>
                        {lastLogin.isSuccess ? '성공' : '실패'}
                      </span>
                    </div>
                    <div className="last-login-meta">
                      <span>{CHANNEL_INFO[lastLogin.channelCode]?.name || lastLogin.channelCode}</span>
                      <span className="separator">·</span>
                      <span>{lastLogin.ipAddress}</span>
                      {lastLogin.location && (
                        <>
                          <span className="separator">·</span>
                          <span>{lastLogin.location}</span>
                        </>
                      )}
                    </div>
                    <div className="last-login-time">
                      {new Date(lastLogin.createdAt).toLocaleString('ko-KR', {
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit'
                      })}
                    </div>
                  </div>
                </div>
              </div>
            )}

            <div className="info-card">
              <h3>회원 정보</h3>
              <div className="info-row">
                <span className="info-label">이메일</span>
                <span className="info-value">{user.email || '-'}</span>
              </div>
              <div className="info-row">
                <span className="info-label">닉네임</span>
                <span className="info-value">{user.nickname || '-'}</span>
              </div>
              <div className="info-row">
                <span className="info-label">상태</span>
                <span className={`status-badge ${user.status?.toLowerCase()}`}>
                  {user.status}
                </span>
              </div>
            </div>

            <div className="info-card">
              <h3>연결된 계정</h3>
              <div className="channel-badges">
                {user.channels?.map((channel, index) => (
                  <span
                    key={index}
                    className="channel-badge"
                    style={{
                      backgroundColor: CHANNEL_INFO[channel.channelCode]?.color || '#6c757d',
                      color: CHANNEL_INFO[channel.channelCode]?.textColor || '#fff'
                    }}
                  >
                    <span className="channel-icon">{CHANNEL_INFO[channel.channelCode]?.icon}</span>
                    {CHANNEL_INFO[channel.channelCode]?.name || channel.channelCode}
                  </span>
                ))}
              </div>
            </div>
          </div>
        )}

        {activeTab === 'profile' && (
          <div className="tab-content">
            <div className="info-card">
              <h3>프로필 관리</h3>

              <div className="profile-item">
                <div className="profile-item-info">
                  <div className="profile-item-icon blue">😊</div>
                  <div className="profile-item-text">
                    <span className="profile-item-label">닉네임</span>
                    <span className="profile-item-value">
                      {user.nickname || <span className="status-tag default">미설정</span>}
                    </span>
                  </div>
                </div>
                <button className="edit-btn" onClick={() => openModal('nickname', user.nickname)}>
                  변경
                </button>
              </div>

              <div className="profile-item">
                <div className="profile-item-info">
                  <div className="profile-item-icon green">📱</div>
                  <div className="profile-item-text">
                    <span className="profile-item-label">핸드폰 번호</span>
                    <span className="profile-item-value">
                      {user.phone || <span className="status-tag default">미등록</span>}
                    </span>
                  </div>
                </div>
                <button className="edit-btn" onClick={() => openModal('phone', user.phone)}>
                  {user.phone ? '변경' : '등록'}
                </button>
              </div>

              <div className="profile-item">
                <div className="profile-item-info">
                  <div className="profile-item-icon purple">📧</div>
                  <div className="profile-item-text">
                    <span className="profile-item-label">복구 이메일</span>
                    <span className="profile-item-value">
                      {user.recoveryEmail || <span className="status-tag default">미등록</span>}
                    </span>
                  </div>
                </div>
                <button className="edit-btn" onClick={() => openModal('recoveryEmail', user.recoveryEmail)}>
                  {user.recoveryEmail ? '변경' : '등록'}
                </button>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'channels' && (
          <div className="tab-content">
            {error && <div className="error-message">{error}</div>}
            {success && <div className="success-message">{success}</div>}

            <div className="info-card">
              <h3>연동 계정 관리</h3>
              <p className="info-description">
                소셜 계정을 연결하면 해당 계정으로도 로그인할 수 있습니다.
              </p>

              {(() => {
                const linkedCount = channelsStatus?.channels?.filter(ch => ch.linked).length || 0;
                const channelColors = {
                  EMAIL: 'gray',
                  GOOGLE: 'red',
                  KAKAO: 'yellow',
                  NAVER: 'green'
                };

                return ['EMAIL', 'GOOGLE', 'KAKAO', 'NAVER'].map((code) => {
                  const channelData = channelsStatus?.channels?.find(ch => ch.channelCode === code);
                  const isLinked = channelData?.linked || false;
                  const info = CHANNEL_INFO[code];

                  if (code === 'EMAIL' && !isLinked) {
                    return null;
                  }

                  return (
                    <div key={code} className="profile-item">
                      <div className="profile-item-info">
                        <div className={`profile-item-icon ${channelColors[code]}`}>
                          {info.icon}
                        </div>
                        <div className="profile-item-text">
                          <span className="profile-item-label">
                            {info.name}
                            {isLinked && (
                              <span className="status-tag success" style={{ marginLeft: 8 }}>연동됨</span>
                            )}
                          </span>
                          <span className="profile-item-value">
                            {isLinked ? (
                              channelData?.channelEmail || '연결된 계정'
                            ) : (
                              '연결되지 않음'
                            )}
                          </span>
                        </div>
                      </div>
                      {isLinked ? (
                        code !== 'EMAIL' && (
                          <button
                            className="channel-unlink-btn"
                            onClick={() => handleUnlinkChannel(code)}
                            disabled={loading || linkedCount <= 1}
                            title={linkedCount <= 1 ? '최소 1개의 로그인 방법이 필요합니다' : ''}
                          >
                            연동 해제
                          </button>
                        )
                      ) : (
                        <button
                          className="channel-link-btn"
                          onClick={() => handleLinkChannel(code.toLowerCase())}
                          disabled={loading}
                        >
                          연동하기
                        </button>
                      )}
                    </div>
                  );
                });
              })()}
            </div>
          </div>
        )}

        {activeTab === 'security' && (
          <div className="tab-content">
            {/* Security Dashboard */}
            {securityDashboard && (
              <div className="info-card security-dashboard">
                <h3>보안 점수</h3>
                <div className="security-score-section">
                  <div className="security-score-circle" data-level={securityDashboard.securityLevel.toLowerCase()}>
                    <span className="score-value">{securityDashboard.securityScore}</span>
                    <span className="score-max">/100</span>
                  </div>
                  <div className="security-level-info">
                    <span className={`security-level ${securityDashboard.securityLevel.toLowerCase()}`}>
                      {securityDashboard.securityLevel === 'EXCELLENT' && '우수'}
                      {securityDashboard.securityLevel === 'GOOD' && '양호'}
                      {securityDashboard.securityLevel === 'FAIR' && '보통'}
                      {securityDashboard.securityLevel === 'WEAK' && '취약'}
                      {securityDashboard.securityLevel === 'CRITICAL' && '위험'}
                    </span>
                  </div>
                </div>

                <div className="security-factors">
                  {securityDashboard.factors.map((factor, index) => (
                    <div key={index} className={`security-factor ${factor.enabled ? 'enabled' : 'disabled'}`}>
                      <div className="factor-info">
                        <span className="factor-icon">{factor.enabled ? '✓' : '○'}</span>
                        <span className="factor-name">{factor.description}</span>
                      </div>
                      <span className="factor-score">{factor.score}/{factor.maxScore}</span>
                    </div>
                  ))}
                </div>

                {securityDashboard.recommendations.length > 0 && (
                  <div className="security-recommendations">
                    <h4>권장 사항</h4>
                    <ul>
                      {securityDashboard.recommendations.map((rec, index) => (
                        <li key={index}>{rec}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}

            {/* Suspicious Activity */}
            {suspiciousActivity && suspiciousActivity.events && suspiciousActivity.events.length > 0 && (
              <div className={`info-card suspicious-card ${suspiciousActivity.riskLevel?.toLowerCase()}`}>
                <h3>
                  의심스러운 활동 감지
                  <span className={`risk-badge ${suspiciousActivity.riskLevel?.toLowerCase()}`}>
                    {suspiciousActivity.riskLevel === 'HIGH' && '위험'}
                    {suspiciousActivity.riskLevel === 'MEDIUM' && '주의'}
                    {suspiciousActivity.riskLevel === 'LOW' && '참고'}
                    {suspiciousActivity.riskLevel === 'SAFE' && '안전'}
                  </span>
                </h3>
                <div className="suspicious-events">
                  {suspiciousActivity.events.map((event, i) => (
                    <div key={i} className={`suspicious-event ${event.severity?.toLowerCase()}`}>
                      <div className="suspicious-event-header">
                        <span className="suspicious-event-type">
                          {event.type === 'BRUTE_FORCE' && '무차별 대입 시도'}
                          {event.type === 'MULTIPLE_LOCATIONS' && '다중 지역 로그인'}
                          {event.type === 'RAPID_LOGIN' && '빠른 연속 로그인'}
                          {event.type === 'UNUSUAL_HOURS' && '비정상 시간대 로그인'}
                          {event.type === 'NEW_DEVICE' && '새 기기 로그인'}
                        </span>
                        <span className={`severity-badge ${event.severity?.toLowerCase()}`}>
                          {event.severity === 'HIGH' ? '높음' : event.severity === 'MEDIUM' ? '중간' : '낮음'}
                        </span>
                      </div>
                      <div className="suspicious-event-desc">{event.description}</div>
                      <div className="suspicious-event-meta">
                        {event.location && <span>{event.location}</span>}
                        {event.detectedAt && (
                          <span>{new Date(event.detectedAt).toLocaleString('ko-KR', {
                            month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
                          })}</span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
                {suspiciousActivity.recommendations?.length > 0 && (
                  <div className="suspicious-recommendations">
                    <h4>권장 조치</h4>
                    <ul>
                      {suspiciousActivity.recommendations.map((rec, i) => (
                        <li key={i}>{rec}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}

            <div className="info-card">
              <h3>보안 설정</h3>

              {hasEmailChannel && (
                <div className="profile-item">
                  <div className="profile-item-info">
                    <div className="profile-item-icon orange">🔑</div>
                    <div className="profile-item-text">
                      <span className="profile-item-label">비밀번호</span>
                      <span className="profile-item-value">••••••••</span>
                    </div>
                  </div>
                  <button className="edit-btn" onClick={() => openModal('password')}>
                    변경
                  </button>
                </div>
              )}

              <div className="profile-item">
                <div className="profile-item-info">
                  <div className="profile-item-icon green">🛡️</div>
                  <div className="profile-item-text">
                    <span className="profile-item-label">2단계 인증 (2FA)</span>
                    <span className="profile-item-value">
                      {twoFactorStatus?.enabled ? (
                        <>
                          <span className="status-tag success">활성화됨</span>
                          {twoFactorStatus.remainingBackupCodes > 0 && (
                            <span style={{ color: '#888', fontSize: 12 }}>
                              백업 코드 {twoFactorStatus.remainingBackupCodes}개 남음
                            </span>
                          )}
                        </>
                      ) : (
                        <span className="status-tag default">비활성화됨</span>
                      )}
                    </span>
                  </div>
                </div>
                <div className="profile-item-actions">
                  {twoFactorStatus?.enabled ? (
                    <>
                      <button className="edit-btn" onClick={() => openModal('2fa-regenerate')}>
                        백업코드 재발급
                      </button>
                      <button className="unlink-btn" onClick={() => openModal('2fa-disable')}>
                        비활성화
                      </button>
                    </>
                  ) : (
                    <button className="edit-btn" onClick={handleSetup2FA} disabled={loading}>
                      {loading ? '설정 중...' : '설정'}
                    </button>
                  )}
                </div>
              </div>

              <div className="profile-item danger">
                <div className="profile-item-info">
                  <div className="profile-item-icon red">⚠️</div>
                  <div className="profile-item-text">
                    <span className="profile-item-label">회원 탈퇴</span>
                    <span className="profile-item-value warning">모든 데이터가 삭제됩니다</span>
                  </div>
                </div>
                <button className="delete-btn" onClick={() => openModal('delete')}>
                  탈퇴
                </button>
              </div>
            </div>


            <div className="info-card">
              <h3>신뢰할 수 있는 기기</h3>
              <p className="info-description">
                신뢰 기기로 등록하면 해당 기기에서의 로그인이 보안 알림 대상에서 제외됩니다. (30일간 유효)
              </p>
              <button
                className="btn btn-small"
                onClick={handleTrustCurrentDevice}
                disabled={loading}
                style={{ marginBottom: 12 }}
              >
                현재 기기 신뢰 등록
              </button>
              {trustedDevices.length > 0 ? (
                <div className="trusted-devices-list">
                  {trustedDevices.map((device) => (
                    <div key={device.deviceId} className="trusted-device-item">
                      <div className="trusted-device-icon">
                        {device.deviceType === 'Mobile' ? '📱' : device.deviceType === 'Tablet' ? '📲' : '💻'}
                      </div>
                      <div className="trusted-device-info">
                        <div className="trusted-device-name">{device.browser} / {device.os}</div>
                        <div className="trusted-device-meta">
                          {device.location && <span>{device.location}</span>}
                          {device.lastUsedAt && (
                            <span className="trusted-device-time">
                              마지막 사용: {new Date(device.lastUsedAt).toLocaleDateString('ko-KR')}
                            </span>
                          )}
                        </div>
                      </div>
                      <button
                        className="channel-unlink-btn"
                        onClick={() => handleRemoveTrustedDevice(device.deviceId)}
                        disabled={loading}
                      >
                        해제
                      </button>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="info-description" style={{ marginTop: 8 }}>등록된 신뢰 기기가 없습니다.</p>
              )}
            </div>

            {/* Notification Settings & Account Lock */}
            {securitySettings && (
              <div className="info-card">
                <h3>알림 설정</h3>

                {securitySettings.accountLocked && (
                  <div className="lock-banner">
                    <span className="lock-banner-icon">🔒</span>
                    <div className="lock-banner-text">
                      <strong>계정이 잠금되었습니다</strong>
                      {securitySettings.lockReason && <p>{securitySettings.lockReason}</p>}
                    </div>
                    <button className="btn btn-small" onClick={handleUnlockAccount} disabled={loading}>
                      잠금 해제
                    </button>
                  </div>
                )}

                <div className="notification-setting-item">
                  <div className="notification-setting-info">
                    <span className="notification-setting-label">로그인 알림</span>
                    <span className="notification-setting-desc">새로운 기기에서 로그인 시 이메일 알림</span>
                  </div>
                  <label className="toggle-switch">
                    <input
                      type="checkbox"
                      checked={securitySettings.loginNotificationEnabled}
                      onChange={handleToggleLoginNotification}
                    />
                    <span className="toggle-slider"></span>
                  </label>
                </div>

                <div className="notification-setting-item">
                  <div className="notification-setting-info">
                    <span className="notification-setting-label">의심 활동 알림</span>
                    <span className="notification-setting-desc">의심스러운 로그인 활동 감지 시 알림</span>
                  </div>
                  <label className="toggle-switch">
                    <input
                      type="checkbox"
                      checked={securitySettings.suspiciousActivityNotificationEnabled}
                      onChange={handleToggleSuspiciousNotification}
                    />
                    <span className="toggle-slider"></span>
                  </label>
                </div>
              </div>
            )}

            <div className="info-card">
              <h3>최근 로그인 기록</h3>
              <div className="login-history-list">
                {loginHistory.length === 0 ? (
                  <p className="info-description">로그인 기록이 없습니다.</p>
                ) : (
                  loginHistory.map((history) => (
                    <div key={history.id} className="login-history-item">
                      <div className="login-history-icon">
                        {history.deviceType === 'Mobile' ? '📱' : history.deviceType === 'Tablet' ? '📲' : '💻'}
                      </div>
                      <div className="login-history-info">
                        <div className="login-history-device">
                          {history.browser} / {history.os}
                          <span className={`login-history-status ${history.isSuccess ? 'success' : 'failed'}`}>
                            {history.isSuccess ? '성공' : '실패'}
                          </span>
                        </div>
                        <div className="login-history-detail">
                          {CHANNEL_INFO[history.channelCode]?.name || history.channelCode} · {history.ipAddress}
                        </div>
                      </div>
                      <div className="login-history-time">
                        {new Date(history.createdAt).toLocaleString('ko-KR', {
                          month: 'short',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit'
                        })}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        )}

        {activeTab === 'activity' && (
          <div className="tab-content">
            {weeklyActivity ? (
              <>
                <div className="info-card">
                  <h3>주간 활동 요약</h3>
                  <p className="info-description">
                    {new Date(weeklyActivity.weekStart).toLocaleDateString('ko-KR')} ~ {new Date(weeklyActivity.weekEnd).toLocaleDateString('ko-KR')}
                  </p>

                  <div className="weekly-stats-grid">
                    <div className="weekly-stat-box">
                      <span className="weekly-stat-value">{weeklyActivity.loginStats?.totalLogins || 0}</span>
                      <span className="weekly-stat-label">총 로그인</span>
                    </div>
                    <div className="weekly-stat-box success">
                      <span className="weekly-stat-value">{weeklyActivity.loginStats?.successfulLogins || 0}</span>
                      <span className="weekly-stat-label">성공</span>
                    </div>
                    <div className="weekly-stat-box danger">
                      <span className="weekly-stat-value">{weeklyActivity.loginStats?.failedLogins || 0}</span>
                      <span className="weekly-stat-label">실패</span>
                    </div>
                  </div>
                </div>

                {weeklyActivity.loginStats?.loginsByChannel?.length > 0 && (
                  <div className="info-card">
                    <h3>채널별 로그인</h3>
                    <div className="channel-login-list">
                      {weeklyActivity.loginStats.loginsByChannel.map((ch, i) => (
                        <div key={i} className="channel-login-item">
                          <span className="channel-badge-small" style={{
                            backgroundColor: CHANNEL_INFO[ch.channel]?.color || '#6c757d',
                            color: CHANNEL_INFO[ch.channel]?.textColor || '#fff'
                          }}>
                            {CHANNEL_INFO[ch.channel]?.icon} {CHANNEL_INFO[ch.channel]?.name || ch.channel}
                          </span>
                          <span className="channel-login-count">{ch.count}회</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {weeklyActivity.deviceBreakdown?.length > 0 && (
                  <div className="info-card">
                    <h3>기기별 접속</h3>
                    <div className="device-breakdown-list">
                      {weeklyActivity.deviceBreakdown.map((device, i) => (
                        <div key={i} className="device-breakdown-item">
                          <span className="device-icon">
                            {device.deviceType === 'Mobile' ? '📱' : device.deviceType === 'Tablet' ? '📲' : '💻'}
                          </span>
                          <span className="device-info">{device.browser} / {device.os}</span>
                          <span className="device-count">{device.count}회</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {weeklyActivity.locations?.length > 0 && (
                  <div className="info-card">
                    <h3>접속 지역</h3>
                    <div className="location-list">
                      {weeklyActivity.locations.map((loc, i) => (
                        <div key={i} className="location-item">
                          <span className="location-name">{loc.location}</span>
                          <span className="location-count">{loc.count}회</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {weeklyActivity.accountEvents?.length > 0 && (
                  <div className="info-card">
                    <h3>계정 활동 이벤트</h3>
                    <div className="account-events-list">
                      {weeklyActivity.accountEvents.map((event, i) => (
                        <div key={i} className="account-event-item">
                          <div className="event-action">{event.action}</div>
                          {event.detail && <div className="event-detail">{event.detail}</div>}
                          <div className="event-time">
                            {new Date(event.occurredAt).toLocaleString('ko-KR', {
                              month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
                            })}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {weeklyActivity.securityAlerts?.length > 0 && (
                  <div className="info-card">
                    <h3>보안 알림</h3>
                    <div className="security-alerts-list">
                      {weeklyActivity.securityAlerts.map((alert, i) => (
                        <div key={i} className="security-alert-item">
                          {alert}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </>
            ) : (
              <div className="info-card">
                <p className="info-description">주간 활동 데이터를 불러오는 중...</p>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Nickname Modal */}
      {modal === 'nickname' && (
        <div className="modal-overlay" onClick={resetModal}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>닉네임 변경</h2>
              <button className="modal-close" onClick={resetModal}>×</button>
            </div>
            <div className="modal-body">
              {error && <div className="error-message">{error}</div>}
              {success && <div className="success-message">{success}</div>}
              <div className="form-group">
                <label>새 닉네임</label>
                <input
                  type="text"
                  value={nickname}
                  onChange={(e) => setNickname(e.target.value)}
                  placeholder="2~20자 닉네임"
                  minLength={2}
                  maxLength={20}
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={resetModal}>취소</button>
              <button className="btn btn-primary" onClick={handleUpdateNickname} disabled={loading}>
                {loading ? '변경 중...' : '변경'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Phone Modal */}
      {modal === 'phone' && (
        <div className="modal-overlay" onClick={resetModal}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>핸드폰 번호 {user.phone ? '변경' : '등록'}</h2>
              <button className="modal-close" onClick={resetModal}>×</button>
            </div>
            <div className="modal-body">
              {error && <div className="error-message">{error}</div>}
              {success && <div className="success-message">{success}</div>}

              {phoneStep === 1 && (
                <div className="form-group">
                  <label>핸드폰 번호</label>
                  <div className="input-with-button">
                    <input
                      type="tel"
                      value={phone}
                      onChange={(e) => setPhone(e.target.value)}
                      placeholder="010-1234-5678"
                    />
                    <button className="btn btn-small" onClick={handleSendPhoneCode} disabled={loading}>
                      {loading ? '전송 중...' : '인증번호 전송'}
                    </button>
                  </div>
                </div>
              )}

              {phoneStep === 2 && (
                <>
                  <div className="form-group">
                    <label>핸드폰 번호</label>
                    <input type="tel" value={phone} disabled />
                  </div>
                  <div className="form-group">
                    <label>인증번호</label>
                    <div className="input-with-button">
                      <input
                        type="text"
                        value={phoneCode}
                        onChange={(e) => setPhoneCode(e.target.value)}
                        placeholder="6자리 인증번호"
                        maxLength={6}
                      />
                      <button className="btn btn-small" onClick={handleVerifyPhoneCode} disabled={loading}>
                        {loading ? '확인 중...' : '확인'}
                      </button>
                    </div>
                  </div>
                </>
              )}

              {phoneStep === 3 && (
                <div className="verification-complete">
                  <span className="check-icon">✓</span>
                  <p>인증이 완료되었습니다</p>
                  <p className="phone-display">{phone}</p>
                </div>
              )}
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={resetModal}>취소</button>
              {phoneStep === 3 && (
                <button className="btn btn-primary" onClick={handleUpdatePhone} disabled={loading}>
                  {loading ? '저장 중...' : '저장'}
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Recovery Email Modal */}
      {modal === 'recoveryEmail' && (
        <div className="modal-overlay" onClick={resetModal}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>복구 이메일 {user.recoveryEmail ? '변경' : '등록'}</h2>
              <button className="modal-close" onClick={resetModal}>×</button>
            </div>
            <div className="modal-body">
              {error && <div className="error-message">{error}</div>}
              {success && <div className="success-message">{success}</div>}

              {!hasEmailChannel && (
                <div className="info-message">
                  복구 이메일은 이메일(비밀번호) 로그인 계정의 비밀번호 재설정에 사용됩니다. 현재 소셜 로그인만 사용 중이라면 별도로 등록하지 않아도 됩니다.
                </div>
              )}

              {emailStep === 1 && (
                <div className="form-group">
                  <label>복구 이메일</label>
                  <div className="input-with-button">
                    <input
                      type="email"
                      value={recoveryEmail}
                      onChange={(e) => setRecoveryEmail(e.target.value)}
                      placeholder="recovery@email.com"
                    />
                    <button className="btn btn-small" onClick={handleSendEmailCode} disabled={loading}>
                      {loading ? '전송 중...' : '인증 코드 전송'}
                    </button>
                  </div>
                </div>
              )}

              {emailStep === 2 && (
                <>
                  <div className="form-group">
                    <label>복구 이메일</label>
                    <input type="email" value={recoveryEmail} disabled />
                  </div>
                  <div className="form-group">
                    <label>인증 코드</label>
                    <div className="input-with-button">
                      <input
                        type="text"
                        value={emailCode}
                        onChange={(e) => setEmailCode(e.target.value)}
                        placeholder="6자리 인증 코드"
                        maxLength={6}
                      />
                      <button className="btn btn-small" onClick={handleVerifyEmailCode} disabled={loading}>
                        {loading ? '확인 중...' : '확인'}
                      </button>
                    </div>
                  </div>
                </>
              )}

              {emailStep === 3 && (
                <div className="verification-complete">
                  <span className="check-icon">✓</span>
                  <p>인증이 완료되었습니다</p>
                  <p className="email-display">{recoveryEmail}</p>
                </div>
              )}
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={resetModal}>취소</button>
              {emailStep === 3 && (
                <button className="btn btn-primary" onClick={handleUpdateRecoveryEmail} disabled={loading}>
                  {loading ? '저장 중...' : '저장'}
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Password Change Modal */}
      {modal === 'password' && (
        <div className="modal-overlay" onClick={resetModal}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>비밀번호 변경</h2>
              <button className="modal-close" onClick={resetModal}>×</button>
            </div>
            <div className="modal-body">
              {error && <div className="error-message">{error}</div>}
              {success && <div className="success-message">{success}</div>}
              <div className="form-group">
                <label>현재 비밀번호</label>
                <div className="input-wrapper">
                  <input
                    type={showCurrentPassword ? 'text' : 'password'}
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    placeholder="현재 비밀번호"
                  />
                  <span className="input-icon" onClick={() => setShowCurrentPassword(!showCurrentPassword)}>
                    {showCurrentPassword ? '🙈' : '👁'}
                  </span>
                </div>
              </div>
              <div className="form-group">
                <label>새 비밀번호</label>
                <div className="input-wrapper">
                  <input
                    type={showNewPassword ? 'text' : 'password'}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="영문, 숫자, 특수문자 포함 8자 이상"
                  />
                  <span className="input-icon" onClick={() => setShowNewPassword(!showNewPassword)}>
                    {showNewPassword ? '🙈' : '👁'}
                  </span>
                </div>
                <PasswordStrengthMeter password={newPassword} />
              </div>
              <div className="form-group">
                <label>새 비밀번호 확인</label>
                <input
                  type="password"
                  value={newPasswordConfirm}
                  onChange={(e) => setNewPasswordConfirm(e.target.value)}
                  placeholder="새 비밀번호 확인"
                />
              </div>
              <p className="info-text">
                비밀번호 변경 시 모든 기기에서 로그아웃됩니다.
              </p>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={resetModal}>취소</button>
              <button className="btn btn-primary" onClick={handleChangePassword} disabled={loading}>
                {loading ? '변경 중...' : '변경'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Account Modal */}
      {modal === 'delete' && (
        <div className="modal-overlay" onClick={resetModal}>
          <div className="modal delete-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>회원 탈퇴</h2>
              <button className="modal-close" onClick={resetModal}>×</button>
            </div>
            <div className="modal-body">
              {error && <div className="error-message">{error}</div>}
              <div className="warning-box">
                <span className="warning-icon">⚠️</span>
                <div>
                  <p><strong>주의: 이 작업은 되돌릴 수 없습니다.</strong></p>
                  <ul>
                    <li>모든 계정 데이터가 삭제됩니다</li>
                    <li>연결된 모든 소셜 계정이 해제됩니다</li>
                    <li>동일 이메일로 재가입이 불가능할 수 있습니다</li>
                  </ul>
                </div>
              </div>
              <div className="form-group">
                <label>확인을 위해 <strong>"회원탈퇴"</strong>를 입력해주세요</label>
                <input
                  type="text"
                  value={deleteConfirm}
                  onChange={(e) => setDeleteConfirm(e.target.value)}
                  placeholder="회원탈퇴"
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={resetModal}>취소</button>
              <button
                className="btn btn-danger"
                onClick={handleDeleteAccount}
                disabled={loading || deleteConfirm !== '회원탈퇴'}
              >
                {loading ? '처리 중...' : '탈퇴하기'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 2FA Setup Modal */}
      {modal === '2fa-setup' && twoFactorSetup && (
        <div className="modal-overlay" onClick={resetModal}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>2단계 인증 설정</h2>
              <button className="modal-close" onClick={resetModal}>×</button>
            </div>
            <div className="modal-body">
              {error && <div className="error-message">{error}</div>}
              <p className="info-description">
                Google Authenticator 또는 유사한 앱으로 QR 코드를 스캔하세요.
              </p>
              <div style={{ textAlign: 'center', margin: '20px 0' }}>
                <img
                  src={twoFactorSetup.qrCodeDataUrl}
                  alt="2FA QR Code"
                  style={{ maxWidth: 200, border: '1px solid #eee', padding: 10, borderRadius: 8 }}
                />
              </div>
              <p className="info-text" style={{ textAlign: 'center', marginBottom: 16 }}>
                수동 입력: <code style={{ background: '#f5f5f5', padding: '2px 6px', borderRadius: 4 }}>
                  {twoFactorSetup.secret}
                </code>
              </p>
              <div className="form-group">
                <label>인증 코드 (6자리)</label>
                <input
                  type="text"
                  value={twoFactorCode}
                  onChange={(e) => setTwoFactorCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="000000"
                  maxLength={6}
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={resetModal}>취소</button>
              <button
                className="btn btn-primary"
                onClick={handleEnable2FA}
                disabled={loading || twoFactorCode.length !== 6}
              >
                {loading ? '확인 중...' : '활성화'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 2FA Backup Codes Modal */}
      {modal === '2fa-backup' && backupCodes.length > 0 && (
        <div className="modal-overlay">
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>백업 코드</h2>
            </div>
            <div className="modal-body">
              <div className="success-message">2단계 인증이 활성화되었습니다!</div>
              <p className="info-description">
                아래 백업 코드를 안전한 곳에 저장하세요. 인증 앱에 접근할 수 없을 때 사용할 수 있습니다.
              </p>
              <div style={{
                background: '#f5f5f5',
                padding: 16,
                borderRadius: 8,
                fontFamily: 'monospace',
                display: 'grid',
                gridTemplateColumns: 'repeat(2, 1fr)',
                gap: 8,
                margin: '16px 0'
              }}>
                {backupCodes.map((code, index) => (
                  <div key={index} style={{ textAlign: 'center', padding: 4 }}>
                    {code}
                  </div>
                ))}
              </div>
              <p className="info-text" style={{ color: '#ff4d4f' }}>
                각 백업 코드는 한 번만 사용할 수 있습니다.
              </p>
            </div>
            <div className="modal-footer">
              <button className="btn btn-primary" onClick={resetModal}>
                확인
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 2FA Disable Modal */}
      {modal === '2fa-disable' && (
        <div className="modal-overlay" onClick={resetModal}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>2단계 인증 비활성화</h2>
              <button className="modal-close" onClick={resetModal}>×</button>
            </div>
            <div className="modal-body">
              {error && <div className="error-message">{error}</div>}
              {success && <div className="success-message">{success}</div>}
              <p className="info-description">
                2단계 인증을 비활성화하려면 현재 인증 코드를 입력하세요.
              </p>
              <div className="form-group">
                <label>인증 코드 (6자리)</label>
                <input
                  type="text"
                  value={twoFactorCode}
                  onChange={(e) => setTwoFactorCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="000000"
                  maxLength={6}
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={resetModal}>취소</button>
              <button
                className="btn btn-danger"
                onClick={handleDisable2FA}
                disabled={loading || twoFactorCode.length !== 6}
              >
                {loading ? '처리 중...' : '비활성화'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 2FA Regenerate Backup Codes Modal */}
      {modal === '2fa-regenerate' && (
        <div className="modal-overlay" onClick={resetModal}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>백업 코드 재발급</h2>
              <button className="modal-close" onClick={resetModal}>×</button>
            </div>
            <div className="modal-body">
              {error && <div className="error-message">{error}</div>}
              <div className="warning-box" style={{ marginBottom: 16 }}>
                <span className="warning-icon">⚠️</span>
                <div>
                  <p><strong>주의: 기존 백업 코드는 모두 무효화됩니다.</strong></p>
                  <p>새로운 백업 코드가 발급되면 기존 코드는 더 이상 사용할 수 없습니다.</p>
                </div>
              </div>
              <p className="info-description">
                백업 코드를 재발급하려면 현재 인증 코드를 입력하세요.
              </p>
              <div className="form-group">
                <label>인증 코드 (6자리)</label>
                <input
                  type="text"
                  value={twoFactorCode}
                  onChange={(e) => setTwoFactorCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="000000"
                  maxLength={6}
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={resetModal}>취소</button>
              <button
                className="btn btn-primary"
                onClick={handleRegenerateBackupCodes}
                disabled={loading || twoFactorCode.length !== 6}
              >
                {loading ? '재발급 중...' : '재발급'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
