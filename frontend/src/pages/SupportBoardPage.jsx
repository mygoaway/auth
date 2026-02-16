import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { supportApi } from '../api/support';

const CATEGORY_LABELS = {
  ACCOUNT: '계정',
  LOGIN: '로그인',
  SECURITY: '보안',
  OTHER: '기타',
};

const STATUS_LABELS = {
  OPEN: '대기중',
  IN_PROGRESS: '처리중',
  RESOLVED: '해결됨',
  CLOSED: '종료',
};

const STATUS_COLORS = {
  OPEN: '#faad14',
  IN_PROGRESS: '#1890ff',
  RESOLVED: '#52c41a',
  CLOSED: '#999',
};

export default function SupportBoardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [posts, setPosts] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [category, setCategory] = useState('');
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(false);
  const [showWriteModal, setShowWriteModal] = useState(false);
  const [form, setForm] = useState({ title: '', content: '', category: 'ACCOUNT', isPrivate: false });
  const [error, setError] = useState('');
  const [submitLoading, setSubmitLoading] = useState(false);

  useEffect(() => {
    loadPosts();
  }, [page, category, status]);

  const loadPosts = async () => {
    setLoading(true);
    try {
      const response = await supportApi.getPosts({
        page,
        size: 10,
        category: category || undefined,
        status: status || undefined,
      });
      setPosts(response.data.content);
      setTotalPages(response.data.totalPages);
    } catch (err) {
      console.error('Failed to load posts', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async () => {
    if (!form.title.trim() || !form.content.trim()) {
      setError('제목과 내용을 입력해주세요');
      return;
    }
    setError('');
    setSubmitLoading(true);
    try {
      await supportApi.createPost(form);
      setShowWriteModal(false);
      setForm({ title: '', content: '', category: 'ACCOUNT', isPrivate: false });
      setPage(0);
      loadPosts();
    } catch (err) {
      setError(err.response?.data?.error?.message || '글 작성에 실패했습니다');
    } finally {
      setSubmitLoading(false);
    }
  };

  const resetModal = () => {
    setShowWriteModal(false);
    setForm({ title: '', content: '', category: 'ACCOUNT', isPrivate: false });
    setError('');
  };

  return (
    <div className="dashboard-container">
      <nav className="dashboard-navbar">
        <div className="navbar-content">
          <div className="navbar-brand" style={{ cursor: 'pointer' }} onClick={() => navigate('/dashboard')}>
            Authly
          </div>
          <div style={{ color: '#fff', fontWeight: 600, fontSize: 16 }}>
            고객센터
          </div>
        </div>
      </nav>

      <div className="dashboard-content">
        {/* Filters and Write Button */}
        <div className="support-header">
          <div className="support-filters">
            <select
              className="support-select"
              value={category}
              onChange={(e) => { setCategory(e.target.value); setPage(0); }}
            >
              <option value="">전체 카테고리</option>
              {Object.entries(CATEGORY_LABELS).map(([key, label]) => (
                <option key={key} value={key}>{label}</option>
              ))}
            </select>
            <select
              className="support-select"
              value={status}
              onChange={(e) => { setStatus(e.target.value); setPage(0); }}
            >
              <option value="">전체 상태</option>
              {Object.entries(STATUS_LABELS).map(([key, label]) => (
                <option key={key} value={key}>{label}</option>
              ))}
            </select>
          </div>
          <button className="btn btn-primary btn-small" onClick={() => setShowWriteModal(true)}>
            글쓰기
          </button>
        </div>

        {/* Post List */}
        <div className="info-card">
          {loading ? (
            <p className="info-description" style={{ textAlign: 'center', padding: 20 }}>
              불러오는 중...
            </p>
          ) : posts.length === 0 ? (
            <p className="info-description" style={{ textAlign: 'center', padding: 20 }}>
              게시글이 없습니다.
            </p>
          ) : (
            <div className="support-post-list">
              {posts.map((post) => (
                <div
                  key={post.id}
                  className="support-post-item"
                  onClick={() => navigate(`/support/${post.id}`)}
                >
                  <div className="support-post-main">
                    <div className="support-post-title-row">
                      {post.private && <span className="support-private-badge">비공개</span>}
                      <span className="support-category-badge">{CATEGORY_LABELS[post.category]}</span>
                      <span className="support-post-title">{post.title}</span>
                    </div>
                    <div className="support-post-meta">
                      <span>{post.authorNickname}</span>
                      <span className="separator">·</span>
                      <span>{new Date(post.createdAt).toLocaleDateString('ko-KR')}</span>
                      <span className="separator">·</span>
                      <span>조회 {post.viewCount}</span>
                      {post.commentCount > 0 && (
                        <>
                          <span className="separator">·</span>
                          <span>댓글 {post.commentCount}</span>
                        </>
                      )}
                    </div>
                  </div>
                  <span
                    className="support-status-badge"
                    style={{ backgroundColor: STATUS_COLORS[post.status] + '20', color: STATUS_COLORS[post.status] }}
                  >
                    {STATUS_LABELS[post.status]}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="support-pagination">
            <button
              className="support-page-btn"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
            >
              이전
            </button>
            {Array.from({ length: totalPages }, (_, i) => (
              <button
                key={i}
                className={`support-page-btn ${page === i ? 'active' : ''}`}
                onClick={() => setPage(i)}
              >
                {i + 1}
              </button>
            ))}
            <button
              className="support-page-btn"
              disabled={page >= totalPages - 1}
              onClick={() => setPage(page + 1)}
            >
              다음
            </button>
          </div>
        )}
      </div>

      {/* Write Modal */}
      {showWriteModal && (
        <div className="modal-overlay" onClick={resetModal}>
          <div className="modal" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 560 }}>
            <div className="modal-header">
              <h2>글쓰기</h2>
              <button className="modal-close" onClick={resetModal}>×</button>
            </div>
            <div className="modal-body">
              {error && <div className="error-message">{error}</div>}
              <div className="form-group">
                <label>카테고리</label>
                <select
                  className="support-select"
                  style={{ width: '100%' }}
                  value={form.category}
                  onChange={(e) => setForm({ ...form, category: e.target.value })}
                >
                  {Object.entries(CATEGORY_LABELS).map(([key, label]) => (
                    <option key={key} value={key}>{label}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>제목</label>
                <input
                  type="text"
                  value={form.title}
                  onChange={(e) => setForm({ ...form, title: e.target.value })}
                  placeholder="제목을 입력하세요"
                  maxLength={200}
                />
              </div>
              <div className="form-group">
                <label>내용</label>
                <textarea
                  className="support-textarea"
                  value={form.content}
                  onChange={(e) => setForm({ ...form, content: e.target.value })}
                  placeholder="내용을 입력하세요"
                  rows={8}
                />
              </div>
              <label className="support-checkbox-label">
                <input
                  type="checkbox"
                  checked={form.isPrivate}
                  onChange={(e) => setForm({ ...form, isPrivate: e.target.checked })}
                />
                <span>비공개 (본인과 관리자만 볼 수 있습니다)</span>
              </label>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={resetModal}>취소</button>
              <button className="btn btn-primary" onClick={handleSubmit} disabled={submitLoading}>
                {submitLoading ? '등록 중...' : '등록'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
