import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { MainLayout } from '../layout/MainLayout';
import { LoginPage } from '../pages/LoginPage';
import { PlaceholderPage } from '../pages/PlaceholderPage';
import { PROTECTED_ROUTE_DEFINITIONS } from '../config/appRoutes';
import { ProtectedRoute } from './ProtectedRoute';
import { PublicOnlyRoute } from './PublicOnlyRoute';
import { RoleRoute } from './RoleRoute';

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<PublicOnlyRoute />}>
          <Route path="/login" element={<LoginPage />} />
        </Route>

        <Route element={<ProtectedRoute />}>
          <Route element={<MainLayout />}>
            {PROTECTED_ROUTE_DEFINITIONS.map((route) =>
              route.index ? (
                <Route key={route.id} index element={<RoleRoute element={route.element} allowedRoles={route.allowedRoles} />} />
              ) : (
                <Route
                  key={route.id}
                  path={route.path}
                  element={<RoleRoute element={route.element} allowedRoles={route.allowedRoles} />}
                />
              ),
            )}
            <Route path="*" element={<PlaceholderPage />} />
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
