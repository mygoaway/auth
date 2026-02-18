import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { adminApi } from '../api/admin';

const TABS = [
  { key: 'overview', label: '개요' },
  { key: 'users', label: '사용자 관리' },
  { key: 'security', label: '보안' },
  { key: 'support', label: '고객센터' },
];

const STATUS_OPTIONS = [
  { value: '', label: '전체' },
  { value: 'ACTIVE', label: '활성' },
  { value: 'DORMANT', label: '휴면' },
  { value: 'LOCKED', label: '잠금' },
  { value: 'PENDING_DELETE', label: '삭제대기' },
];

const ROLE_OPTIONS = ['USER', 'ADMIN'];

const STATUS_CHANGE_OPTIONS = ['ACTIVE', 'DORMANT', 'LOCKED', 'PENDING_DELETE'];

export default function AdminPage() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [activeTab, setActiveTab] = useState(() => {
    const tab = searchParams.get('tab');
    return TABS.some(t => t.key === tab) ? tab : 'overview';
  });

  // Overview
  const [dashboard, setDashboard] = useState(null);
  const [loginStats, setLoginStats] = useState(null);
  const [supportStats, setSupportStats] = useState(null);

  // Users
  const [users, setUsers] = useState([]);
  const [userSearch, setUserSearch] = useState('');
  const [userStatusFilter, setUserStatusFilter] = useState('');
  const [userPage, setUserPage] = useState(0);
  const [userTotalPages, setUserTotalPages] = useState(0);
  const [userTotalElements, setUserTotalElements] = useState(0);

  // Security
  const [securityEvents, setSecurityEvents] = useState(null);

  // Support stats (reuse)
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleTabChange = (tab) => {
    setActiveTab(tab);
    setSearchParams({ tab });
    setError('');
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  // Overview data
  const loadOverview = useCallback(async () => {
    setLoading(true);
    try {
      const [dashRes, loginRes, supportRes] = await Promise.all([
        adminApi.getDashboard(),
        adminApi.getLoginStats(),
        adminApi.getSupportStats(),
      ]);
      setDashboard(dashRes.data);
      setLoginStats(loginRes.data);
      setSupportStats(supportRes.data);
    } catch {
      setError('데이터를 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  // User search
  const loadUsers = useCallback(async (page = 0) => {
    setLoading(true);
    try {
      const res = await adminApi.searchUsers({
        keyword: userSearch || undefined,
        status: userStatusFilter || undefined,
        page,
        size: 20,
      });
      setUsers(res.data.users);
      setUserPage(res.data.currentPage);
      setUserTotalPages(res.data.totalPages);
      setUserTotalElements(res.data.totalElements);
    } catch {
      setError('사용자 검색에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, [userSearch, userStatusFilter]);

  // Security events
  const loadSecurityEvents = useCallback(async () => {
    setLoading(true);
    try {
      const res = await adminApi.getSecurityEvents();
      setSecurityEvents(res.data);
    } catch {
      setError('보안 이벤트를 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  // Support stats
  const loadSupportStats = useCallback(async () => {
    setLoading(true);
    try {
      const res = await adminApi.getSupportStats();
      setSupportStats(res.data);
    } catch {
      setError('고객센터 통계를 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (activeTab === 'overview') loadOverview();
    else if (activeTab === 'users') loadUsers(0);
    else if (activeTab === 'security') loadSecurityEvents();
    else if (activeTab === 'support') loadSupportStats();
  }, [activeTab, loadOverview, loadUsers, loadSecurityEvents, loadSupportStats]);

  // User actions
  const handleRoleChange = async (userId, newRole) => {
    try {
      await adminApi.updateUserRole(userId, newRole);
      loadUsers(userPage);
    } catch {
      setError('역할 변경에 실패했습니다.');
    }
  };

  const handleStatusChange = async (userId, newStatus) => {
    try {
      await adminApi.updateUserStatus(userId, newStatus);
      loadUsers(userPage);
    } catch {
      setError('상태 변경에 실패했습니다.');
    }
  };

  const handleUserSearch = (e) => {
    e.preventDefault();
    setUserPage(0);
    loadUsers(0);
  };

  // Render helpers
  const renderOverview = () => {
    if (!dashboard || !loginStats) return <div className="admin-loading">로딩 중...</div>;

    return (
      <div className="admin-overview">
        <h2 className="admin-section-title">대시보드 개요</h2>

        <div className="admin-stats-grid">
          <div className="admin-stat-card">
            <div className="admin-stat-label">전체 사용자</div>
            <div className="admin-stat-value">{dashboard.userStats.totalUsers.toLocaleString()}</div>
          </div>
          <div className="admin-stat-card">
            <div className="admin-stat-label">활성 사용자</div>
            <div className="admin-stat-value admin-stat-active">{dashboard.userStats.activeUsers.toLocaleString()}</div>
          </div>
          <div className="admin-stat-card">
            <div className="admin-stat-label">휴면 사용자</div>
            <div className="admin-stat-value admin-stat-dormant">{dashboard.userStats.dormantUsers.toLocaleString()}</div>
          </div>
          <div className="admin-stat-card">
            <div className="admin-stat-label">삭제 대기</div>
            <div className="admin-stat-value admin-stat-danger">{dashboard.userStats.pendingDeleteUsers.toLocaleString()}</div>
          </div>
        </div>

        <div className="admin-stats-grid" style={{ marginTop: 16 }}>
          <div className="admin-stat-card">
            <div className="admin-stat-label">오늘 로그인</div>
            <div className="admin-stat-value">{loginStats.todayLogins.toLocaleString()}</div>
          </div>
          <div className="admin-stat-card">
            <div className="admin-stat-label">오늘 가입</div>
            <div className="admin-stat-value">{loginStats.todaySignups.toLocaleString()}</div>
          </div>
          <div className="admin-stat-card">
            <div className="admin-stat-label">7일 활성 사용자</div>
            <div className="admin-stat-value">{loginStats.activeUsersLast7Days.toLocaleString()}</div>
          </div>
          {supportStats && (
            <div className="admin-stat-card">
              <div className="admin-stat-label">미처리 문의</div>
              <div className="admin-stat-value admin-stat-danger">{supportStats.openPosts}</div>
            </div>
          )}
        </div>

        {/* Daily login chart (simple table) */}
        {loginStats.dailyLogins && loginStats.dailyLogins.length > 0 && (
          <div className="admin-chart-section">
            <h3 className="admin-subsection-title">일별 로그인 추이 (최근 7일)</h3>
            <div className="admin-bar-chart">
              {loginStats.dailyLogins.map((item, idx) => {
                const maxCount = Math.max(...loginStats.dailyLogins.map(d => d.count), 1);
                const barHeight = Math.max((item.count / maxCount) * 120, 4);
                return (
                  <div key={idx} className="admin-bar-item">
                    <div className="admin-bar-count">{item.count}</div>
                    <div className="admin-bar" style={{ height: barHeight }} />
                    <div className="admin-bar-label">{item.date.substring(5)}</div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Recent Users */}
        {dashboard.recentUsers && dashboard.recentUsers.length > 0 && (
          <div className="admin-chart-section">
            <h3 className="admin-subsection-title">최근 가입 사용자</h3>
            <div className="admin-table-wrapper">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>이메일</th>
                    <th>닉네임</th>
                    <th>상태</th>
                    <th>채널</th>
                    <th>가입일</th>
                  </tr>
                </thead>
                <tbody>
                  {dashboard.recentUsers.slice(0, 10).map(u => (
                    <tr key={u.userId}>
                      <td>{u.userId}</td>
                      <td>{u.email || '-'}</td>
                      <td>{u.nickname || '-'}</td>
                      <td><span className={`admin-badge admin-badge-${u.status.toLowerCase()}`}>{u.status}</span></td>
                      <td>{u.channels?.join(', ') || '-'}</td>
                      <td>{u.createdAt?.substring(0, 10) || '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    );
  };

  const renderUsers = () => (
    <div className="admin-users">
      <h2 className="admin-section-title">사용자 관리</h2>

      <form className="admin-search-form" onSubmit={handleUserSearch}>
        <input
          type="text"
          className="admin-search-input"
          placeholder="이메일, UUID로 검색..."
          value={userSearch}
          onChange={e => setUserSearch(e.target.value)}
        />
        <select
          className="admin-search-select"
          value={userStatusFilter}
          onChange={e => { setUserStatusFilter(e.target.value); }}
        >
          {STATUS_OPTIONS.map(opt => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>
        <button type="submit" className="admin-search-btn">검색</button>
      </form>

      <div className="admin-search-result-info">
        총 {userTotalElements.toLocaleString()}명
      </div>

      <div className="admin-table-wrapper">
        <table className="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>이메일</th>
              <th>닉네임</th>
              <th>상태</th>
              <th>역할</th>
              <th>채널</th>
              <th>가입일</th>
              <th>마지막 로그인</th>
              <th>관리</th>
            </tr>
          </thead>
          <tbody>
            {users.length === 0 ? (
              <tr><td colSpan={9} style={{ textAlign: 'center', padding: 32 }}>검색 결과가 없습니다.</td></tr>
            ) : (
              users.map(u => (
                <tr key={u.userId}>
                  <td>{u.userId}</td>
                  <td>{u.email || '-'}</td>
                  <td>{u.nickname || '-'}</td>
                  <td>
                    <select
                      className="admin-inline-select"
                      value={u.status}
                      onChange={e => handleStatusChange(u.userId, e.target.value)}
                    >
                      {STATUS_CHANGE_OPTIONS.map(s => <option key={s} value={s}>{s}</option>)}
                    </select>
                  </td>
                  <td>
                    <select
                      className="admin-inline-select"
                      value={u.role}
                      onChange={e => handleRoleChange(u.userId, e.target.value)}
                    >
                      {ROLE_OPTIONS.map(r => <option key={r} value={r}>{r}</option>)}
                    </select>
                  </td>
                  <td>{u.channels?.join(', ') || '-'}</td>
                  <td>{u.createdAt?.substring(0, 10) || '-'}</td>
                  <td>{u.lastLoginAt?.substring(0, 10) || '-'}</td>
                  <td>
                    <button
                      className="admin-action-btn"
                      onClick={() => handleStatusChange(u.userId, u.status === 'LOCKED' ? 'ACTIVE' : 'LOCKED')}
                    >
                      {u.status === 'LOCKED' ? '잠금해제' : '잠금'}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {userTotalPages > 1 && (
        <div className="admin-pagination">
          <button
            className="admin-page-btn"
            disabled={userPage === 0}
            onClick={() => loadUsers(userPage - 1)}
          >
            이전
          </button>
          <span className="admin-page-info">{userPage + 1} / {userTotalPages}</span>
          <button
            className="admin-page-btn"
            disabled={userPage >= userTotalPages - 1}
            onClick={() => loadUsers(userPage + 1)}
          >
            다음
          </button>
        </div>
      )}
    </div>
  );

  const renderSecurity = () => {
    if (!securityEvents) return <div className="admin-loading">로딩 중...</div>;

    return (
      <div className="admin-security">
        <h2 className="admin-section-title">보안 모니터링</h2>

        <div className="admin-stats-grid">
          <div className="admin-stat-card admin-stat-card-alert">
            <div className="admin-stat-label">오늘 로그인 실패</div>
            <div className="admin-stat-value admin-stat-danger">{securityEvents.failedLoginsToday}</div>
          </div>
          <div className="admin-stat-card">
            <div className="admin-stat-label">오늘 비밀번호 변경</div>
            <div className="admin-stat-value">{securityEvents.passwordChangesToday}</div>
          </div>
          <div className="admin-stat-card admin-stat-card-alert">
            <div className="admin-stat-label">오늘 계정 잠금</div>
            <div className="admin-stat-value admin-stat-danger">{securityEvents.accountLocksToday}</div>
          </div>
        </div>

        {/* Failed Logins */}
        <div className="admin-chart-section">
          <h3 className="admin-subsection-title">최근 로그인 실패 (24시간)</h3>
          {securityEvents.recentFailedLogins.length === 0 ? (
            <p className="admin-empty-text">최근 로그인 실패가 없습니다.</p>
          ) : (
            <div className="admin-table-wrapper">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>사용자ID</th>
                    <th>IP</th>
                    <th>브라우저</th>
                    <th>위치</th>
                    <th>사유</th>
                    <th>시간</th>
                  </tr>
                </thead>
                <tbody>
                  {securityEvents.recentFailedLogins.map((item, idx) => (
                    <tr key={idx}>
                      <td>{item.userId || '-'}</td>
                      <td>{item.ipAddress || '-'}</td>
                      <td>{item.browser || '-'}</td>
                      <td>{item.location || '-'}</td>
                      <td>{item.failureReason || '-'}</td>
                      <td>{item.createdAt || '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Audit Events */}
        <div className="admin-chart-section">
          <h3 className="admin-subsection-title">최근 감사 로그 (24시간)</h3>
          {securityEvents.recentAuditEvents.length === 0 ? (
            <p className="admin-empty-text">최근 감사 로그가 없습니다.</p>
          ) : (
            <div className="admin-table-wrapper">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>사용자ID</th>
                    <th>액션</th>
                    <th>대상</th>
                    <th>상세</th>
                    <th>IP</th>
                    <th>성공</th>
                    <th>시간</th>
                  </tr>
                </thead>
                <tbody>
                  {securityEvents.recentAuditEvents.map((item, idx) => (
                    <tr key={idx}>
                      <td>{item.userId || '-'}</td>
                      <td><span className="admin-badge admin-badge-action">{item.action}</span></td>
                      <td>{item.target || '-'}</td>
                      <td className="admin-detail-cell">{item.detail || '-'}</td>
                      <td>{item.ipAddress || '-'}</td>
                      <td>{item.success ? 'O' : 'X'}</td>
                      <td>{item.createdAt || '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    );
  };

  const renderSupport = () => {
    if (!supportStats) return <div className="admin-loading">로딩 중...</div>;

    return (
      <div className="admin-support">
        <h2 className="admin-section-title">고객센터 통계</h2>

        <div className="admin-stats-grid">
          <div className="admin-stat-card">
            <div className="admin-stat-label">전체 게시글</div>
            <div className="admin-stat-value">{supportStats.totalPosts}</div>
          </div>
          <div className="admin-stat-card admin-stat-card-alert">
            <div className="admin-stat-label">대기중</div>
            <div className="admin-stat-value admin-stat-danger">{supportStats.openPosts}</div>
          </div>
          <div className="admin-stat-card">
            <div className="admin-stat-label">처리중</div>
            <div className="admin-stat-value admin-stat-active">{supportStats.inProgressPosts}</div>
          </div>
          <div className="admin-stat-card">
            <div className="admin-stat-label">해결됨</div>
            <div className="admin-stat-value">{supportStats.resolvedPosts}</div>
          </div>
        </div>

        <div className="admin-stats-grid" style={{ marginTop: 16 }}>
          <div className="admin-stat-card">
            <div className="admin-stat-label">종료</div>
            <div className="admin-stat-value">{supportStats.closedPosts}</div>
          </div>
          <div className="admin-stat-card">
            <div className="admin-stat-label">오늘 등록</div>
            <div className="admin-stat-value">{supportStats.todayPosts}</div>
          </div>
        </div>

        <div className="admin-chart-section" style={{ marginTop: 24 }}>
          <button className="admin-search-btn" onClick={() => navigate('/support')}>
            고객센터 게시판으로 이동
          </button>
        </div>
      </div>
    );
  };

  return (
    <div className="dashboard-container">
      <nav className="dashboard-navbar">
        <div className="navbar-content">
          <div className="navbar-brand">Authly Admin</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <button className="logout-btn" onClick={() => navigate('/dashboard')}>
              내 대시보드
            </button>
            <button className="logout-btn" onClick={handleLogout}>
              로그아웃
            </button>
          </div>
        </div>
      </nav>

      <div className="dashboard-content">
        <div className="admin-tab-nav">
          {TABS.map(tab => (
            <button
              key={tab.key}
              className={`admin-tab-btn ${activeTab === tab.key ? 'active' : ''}`}
              onClick={() => handleTabChange(tab.key)}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {error && <div className="admin-error">{error}</div>}
        {loading && <div className="admin-loading">로딩 중...</div>}

        <div className="admin-content">
          {activeTab === 'overview' && renderOverview()}
          {activeTab === 'users' && renderUsers()}
          {activeTab === 'security' && renderSecurity()}
          {activeTab === 'support' && renderSupport()}
        </div>
      </div>
    </div>
  );
}
