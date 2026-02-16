import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { ToastProvider } from './components/Toast';
import ErrorBoundary from './components/ErrorBoundary';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import DashboardPage from './pages/DashboardPage';
import OAuth2CallbackPage from './pages/OAuth2CallbackPage';
import OAuth2LinkCallbackPage from './pages/OAuth2LinkCallbackPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import SupportBoardPage from './pages/SupportBoardPage';
import SupportPostDetailPage from './pages/SupportPostDetailPage';
import AdminPage from './pages/AdminPage';
import './styles/global.css';

function PrivateRoute({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="auth-container">
        <div className="auth-card" style={{ textAlign: 'center' }}>
          <p>로딩 중...</p>
        </div>
      </div>
    );
  }

  return user ? children : <Navigate to="/login" />;
}

function PublicRoute({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="auth-container">
        <div className="auth-card" style={{ textAlign: 'center' }}>
          <p>로딩 중...</p>
        </div>
      </div>
    );
  }

  return user ? <Navigate to="/dashboard" /> : children;
}

function AdminRoute({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="auth-container">
        <div className="auth-card" style={{ textAlign: 'center' }}>
          <p>로딩 중...</p>
        </div>
      </div>
    );
  }

  if (!user) return <Navigate to="/login" />;
  if (user.role !== 'ADMIN') return <Navigate to="/dashboard" />;
  return children;
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<PublicRoute><LoginPage /></PublicRoute>} />
      <Route path="/signup" element={<PublicRoute><SignupPage /></PublicRoute>} />
      <Route path="/forgot-password" element={<PublicRoute><ForgotPasswordPage /></PublicRoute>} />
      <Route path="/oauth2/callback" element={<OAuth2CallbackPage />} />
      <Route path="/oauth2/link/success" element={<PrivateRoute><OAuth2LinkCallbackPage /></PrivateRoute>} />
      <Route path="/dashboard" element={<PrivateRoute><DashboardPage /></PrivateRoute>} />
      <Route path="/admin" element={<AdminRoute><AdminPage /></AdminRoute>} />
      <Route path="/support" element={<PrivateRoute><SupportBoardPage /></PrivateRoute>} />
      <Route path="/support/:postId" element={<PrivateRoute><SupportPostDetailPage /></PrivateRoute>} />
      <Route path="/" element={<Navigate to="/dashboard" />} />
    </Routes>
  );
}

function App() {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <ToastProvider>
          <AuthProvider>
            <AppRoutes />
          </AuthProvider>
        </ToastProvider>
      </BrowserRouter>
    </ErrorBoundary>
  );
}

export default App;
