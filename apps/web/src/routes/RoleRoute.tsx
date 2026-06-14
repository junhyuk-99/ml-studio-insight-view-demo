import { Navigate } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';
import type { UserRole } from '../types/auth';
import type { ReactElement } from 'react';

type RoleRouteProps = {
  element: ReactElement;
  allowedRoles?: UserRole[];
};

export function RoleRoute({ element, allowedRoles }: RoleRouteProps) {
  const { user } = useAuth();

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  const normalizedRole = user.role.toLowerCase();
  const normalizedAllowedRoles = allowedRoles?.map((role) => role.toLowerCase());

  if (normalizedAllowedRoles && !normalizedAllowedRoles.includes(normalizedRole)) {
    return <Navigate to="/" replace />;
  }

  return element;
}
