import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
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

export default function SupportPostDetailPage() {
  const { postId } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [post, setPost] = useState(null);
  const [loading, setLoading] = useState(true);
  const [comment, setComment] = useState('');
  const [commentLoading, setCommentLoading] = useState(false);
  const [error, setError] = useState('');
  const [showEditModal, setShowEditModal] = useState(false);
  const [editForm, setEditForm] = useState({ title: '', content: '', category: 'ACCOUNT', isPrivate: false });
  const [editLoading, setEditLoading] = useState(false);

  const isAdmin = user?.channels?.some(c => c.channelCode === 'ADMIN') || user?.role === 'ADMIN';
  const isAuthor = post && user && post.userId === user.userId;

  useEffect(() => {
    loadPost();
  }, [postId]);

  const loadPost = async () => {
    setLoading(true);
    try {
      const response = await supportApi.getPost(postId);
      setPost(response.data);
    } catch (err) {
      if (err.response?.status === 403) {
        setError('비공개 게시글에 접근할 수 없습니다');
      } else if (err.response?.status === 404) {
        setError('게시글을 찾을 수 없습니다');
      } else {
        setError('게시글을 불러오는데 실패했습니다');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleCommentSubmit = async () => {
    if (!comment.trim()) return;
    setCommentLoading(true);
    try {
      await supportApi.createComment(postId, comment);
      setComment('');
      loadPost();
    } catch (err) {
      setError(err.response?.data?.error?.message || '댓글 작성에 실패했습니다');
    } finally {
      setCommentLoading(false);
    }
  };

  const handleDeleteComment = async (commentId) => {
    if (!window.confirm('댓글을 삭제하시겠습니까?')) return;
    try {
      await supportApi.deleteComment(postId, commentId);
      loadPost();
    } catch (err) {
      setError(err.response?.data?.error?.message || '댓글 삭제에 실패했습니다');
    }
  };

  const handleDeletePost = async () => {
    if (!window.confirm('게시글을 삭제하시겠습니까?')) return;
    try {
      await supportApi.deletePost(postId);
      navigate('/support');
    } catch (err) {
      setError(err.response?.data?.error?.message || '게시글 삭제에 실패했습니다');
    }
  };

  const handleStatusChange = async (newStatus) => {
    try {
      await supportApi.updatePostStatus(postId, newStatus);
      loadPost();
    } catch (err) {
      setError(err.response?.data?.error?.message || '상태 변경에 실패했습니다');
    }
  };

  const openEditModal = () => {
    setEditForm({
      title: post.title,
      content: post.content,
      category: post.category,
      isPrivate: post.isPrivate,
    });
    setShowEditModal(true);
  };

  const handleEditSubmit = async () => {
    if (!editForm.title.trim() || !editForm.content.trim()) {
      setError('제목과 내용을 입력해주세요');
      return;
    }
    setEditLoading(true);
    try {
      await supportApi.updatePost(postId, editForm);
      setShowEditModal(false);
      loadPost();
    } catch (err) {
      setError(err.response?.data?.error?.message || '수정에 실패했습니다');
    } finally {
      setEditLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="dashboard-container">
        <nav className="dashboard-navbar">
          <div className="navbar-content">
            <div className="navbar-brand" style={{ cursor: 'pointer' }} onClick={() => navigate('/dashboard')}>
              Authly
            </div>
          </div>
        </nav>
        <div className="dashboard-content">
          <div className="info-card" style={{ textAlign: 'center', padding: 40 }}>
            <p>불러오는 중...</p>
          </div>
        </div>
      </div>
    );
  }

  if (error && !post) {
    return (
      <div className="dashboard-container">
        <nav className="dashboard-navbar">
          <div className="navbar-content">
            <div className="navbar-brand" style={{ cursor: 'pointer' }} onClick={() => navigate('/dashboard')}>
              Authly
            </div>
          </div>
        </nav>
        <div className="dashboard-content">
          <div className="info-card" style={{ textAlign: 'center', padding: 40 }}>
            <p className="error-message">{error}</p>
            <button className="btn btn-secondary btn-small" onClick={() => navigate('/support')} style={{ marginTop: 16 }}>
              목록으로
            </button>
          </div>
        </div>
      </div>
    );
  }

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
        {error && <div className="error-message">{error}</div>}

        {/* Back Button */}
        <button
          className="btn btn-secondary btn-small"
          onClick={() => navigate('/support')}
          style={{ marginBottom: 16 }}
        >
          목록으로
        </button>

        {/* Post Detail */}
        {post && (
          <>
            <div className="info-card">
              <div className="support-detail-header">
                <div className="support-detail-badges">
                  <span className="support-category-badge">{CATEGORY_LABELS[post.category]}</span>
                  <span
                    className="support-status-badge"
                    style={{ backgroundColor: STATUS_COLORS[post.status] + '20', color: STATUS_COLORS[post.status] }}
                  >
                    {STATUS_LABELS[post.status]}
                  </span>
                  {post.isPrivate && <span className="support-private-badge">비공개</span>}
                </div>
                <h2 className="support-detail-title">{post.title}</h2>
                <div className="support-detail-meta">
                  <span>{post.authorNickname}</span>
                  <span className="separator">·</span>
                  <span>{new Date(post.createdAt).toLocaleString('ko-KR')}</span>
                  <span className="separator">·</span>
                  <span>조회 {post.viewCount}</span>
                </div>
              </div>

              <div className="support-detail-content">
                {post.content.split('\n').map((line, i) => (
                  <p key={i}>{line || '\u00A0'}</p>
                ))}
              </div>

              {/* Actions */}
              <div className="support-detail-actions">
                {isAuthor && (
                  <>
                    <button className="edit-btn" onClick={openEditModal}>수정</button>
                    <button className="channel-unlink-btn" onClick={handleDeletePost}>삭제</button>
                  </>
                )}
                {isAdmin && !isAuthor && (
                  <button className="channel-unlink-btn" onClick={handleDeletePost}>삭제 (관리자)</button>
                )}
                {isAdmin && (
                  <select
                    className="support-select"
                    value={post.status}
                    onChange={(e) => handleStatusChange(e.target.value)}
                  >
                    {Object.entries(STATUS_LABELS).map(([key, label]) => (
                      <option key={key} value={key}>{label}</option>
                    ))}
                  </select>
                )}
              </div>
            </div>

            {/* Comments */}
            <div className="info-card">
              <h3>댓글 ({post.comments?.length || 0})</h3>

              {post.comments?.length > 0 ? (
                <div className="support-comments">
                  {post.comments.map((c) => (
                    <div key={c.id} className={`support-comment ${c.admin ? 'admin' : ''}`}>
                      <div className="support-comment-header">
                        <div className="support-comment-author">
                          <span className="support-comment-nickname">{c.authorNickname}</span>
                          {c.admin && <span className="support-admin-badge">관리자</span>}
                        </div>
                        <div className="support-comment-actions">
                          <span className="support-comment-time">
                            {new Date(c.createdAt).toLocaleString('ko-KR')}
                          </span>
                          {(c.userId === user?.userId || isAdmin) && (
                            <button
                              className="support-comment-delete"
                              onClick={() => handleDeleteComment(c.id)}
                            >
                              삭제
                            </button>
                          )}
                        </div>
                      </div>
                      <div className="support-comment-content">
                        {c.content.split('\n').map((line, i) => (
                          <p key={i}>{line || '\u00A0'}</p>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="info-description">아직 댓글이 없습니다.</p>
              )}

              {/* Comment Form */}
              <div className="support-comment-form">
                <textarea
                  className="support-textarea"
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  placeholder="댓글을 입력하세요"
                  rows={3}
                />
                <button
                  className="btn btn-primary btn-small"
                  onClick={handleCommentSubmit}
                  disabled={commentLoading || !comment.trim()}
                  style={{ alignSelf: 'flex-end' }}
                >
                  {commentLoading ? '등록 중...' : '댓글 등록'}
                </button>
              </div>
            </div>
          </>
        )}
      </div>

      {/* Edit Modal */}
      {showEditModal && (
        <div className="modal-overlay" onClick={() => setShowEditModal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 560 }}>
            <div className="modal-header">
              <h2>글 수정</h2>
              <button className="modal-close" onClick={() => setShowEditModal(false)}>×</button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>카테고리</label>
                <select
                  className="support-select"
                  style={{ width: '100%' }}
                  value={editForm.category}
                  onChange={(e) => setEditForm({ ...editForm, category: e.target.value })}
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
                  value={editForm.title}
                  onChange={(e) => setEditForm({ ...editForm, title: e.target.value })}
                  maxLength={200}
                />
              </div>
              <div className="form-group">
                <label>내용</label>
                <textarea
                  className="support-textarea"
                  value={editForm.content}
                  onChange={(e) => setEditForm({ ...editForm, content: e.target.value })}
                  rows={8}
                />
              </div>
              <label className="support-checkbox-label">
                <input
                  type="checkbox"
                  checked={editForm.isPrivate}
                  onChange={(e) => setEditForm({ ...editForm, isPrivate: e.target.checked })}
                />
                <span>비공개</span>
              </label>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setShowEditModal(false)}>취소</button>
              <button className="btn btn-primary" onClick={handleEditSubmit} disabled={editLoading}>
                {editLoading ? '수정 중...' : '수정'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
