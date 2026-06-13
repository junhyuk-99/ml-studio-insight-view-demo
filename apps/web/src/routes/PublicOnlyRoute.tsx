import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';

export function PublicOnlyRoute() {
  const { isAuthenticated } = useAuth();

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}

