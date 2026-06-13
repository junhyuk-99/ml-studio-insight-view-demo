import { Box, Toolbar } from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { AppHeader } from '../components/layout/AppHeader';
import { AppSidebar } from '../components/layout/AppSidebar';
import { APP_HEADER_HEIGHT } from '../components/layout/layoutConstants';
import { extractAccessiblePaths } from '../config/navigationConfig';
import { menuService } from '../services/menuService';
import { useAuth } from '../store/AuthContext';
import { useSelectedAlgorithm } from '../store/SelectedAlgorithmContext';
import type { MenuMid } from '../types/menu';
import type { MainLayoutOutletContext } from './mainLayoutContext';

export function MainLayout() {
  const { user, signOut } = useAuth();
  const { clearSelectedAlgorithm } = useSelectedAlgorithm();
  const navigate = useNavigate();
  const location = useLocation();

  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [menus, setMenus] = useState<MenuMid[]>([]);
  const [menuLoading, setMenuLoading] = useState(true);
  const [menuError, setMenuError] = useState<string | null>(null);
  const accessiblePaths = useMemo(() => extractAccessiblePaths(menus), [menus]);

  useEffect(() => {
    if (!user) {
      return;
    }

    setMenuLoading(true);
    setMenuError(null);

    menuService
      .getMenu(user.role)
      .then((response) => {
        setMenus(response.menus);
      })
      .catch((error: unknown) => {
        setMenuError(error instanceof Error ? error.message : '메뉴 조회에 실패했습니다.');
      })
      .finally(() => {
        setMenuLoading(false);
      });
  }, [user]);

  const handleNavigate = useCallback(
    (path: string) => {
      navigate(path);
      setSidebarOpen(false);
    },
    [navigate],
  );

  const handleLogout = useCallback(() => {
    signOut();
    clearSelectedAlgorithm();
    navigate('/login', { replace: true });
  }, [clearSelectedAlgorithm, navigate, signOut]);

  useEffect(() => {
    if (menuLoading || menuError) {
      return;
    }

    if (location.pathname === '/') {
      return;
    }

    if (!accessiblePaths.has(location.pathname)) {
      navigate('/', { replace: true });
    }
  }, [accessiblePaths, location.pathname, menuError, menuLoading, navigate]);

  if (!user) {
    return null;
  }

  const outletContext: MainLayoutOutletContext = {
    menus,
    menuLoading,
    menuError,
  };

  return (
    <Box sx={{ minHeight: '100vh', background: 'linear-gradient(180deg, #f4f7ff 0%, #eef8f3 100%)' }}>
      <AppHeader user={user} onMenuClick={() => setSidebarOpen(true)} onLogout={handleLogout} />

      <AppSidebar
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        menus={menus}
        loading={menuLoading}
        error={menuError}
        activePath={location.pathname}
        onNavigate={handleNavigate}
      />

      <Box component="main" sx={{ minWidth: 0 }}>
        <Toolbar sx={{ minHeight: APP_HEADER_HEIGHT }} />
        <Box sx={{ p: { xs: 1.5, sm: 2, md: 3 } }}>
          <Outlet context={outletContext} />
        </Box>
      </Box>
    </Box>
  );
}
