import { Alert, Box, Divider, Drawer, ListItemButton, ListItemIcon, ListItemText, Typography } from '@mui/material';
import { useMemo } from 'react';
import {
  buildSidebarMenuSections,
  getRouteMetaById,
  type SidebarMenuSection,
} from '../../config/navigationConfig';
import { APP_HEADER_HEIGHT, APP_SIDEBAR_WIDTH } from './layoutConstants';
import { SidebarNavigation } from './sidebar/SidebarNavigation';
import type { MenuMid } from '../../types/menu';

type AppSidebarProps = {
  open: boolean;
  onClose: () => void;
  menus: MenuMid[];
  loading: boolean;
  error: string | null;
  activePath: string;
  onNavigate: (path: string) => void;
};

function SidebarContent({
  sections,
  activePath,
  loading,
  error,
  onNavigate,
}: {
  sections: SidebarMenuSection[];
  activePath: string;
  loading: boolean;
  error: string | null;
  onNavigate: (path: string) => void;
}) {
  const homeRouteMeta = getRouteMetaById('home');
  const HomeIcon = homeRouteMeta.icon;
  const isHomeActive = activePath === homeRouteMeta.path;

  return (
    <Box
      sx={{
        height: '100%',
        px: 1.2,
        py: 1.5,
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: '#ffffff',
        color: '#111827',
      }}
    >

      <ListItemButton
        selected={isHomeActive}
        onClick={() => onNavigate(homeRouteMeta.path)}
        sx={{
          borderRadius: 1.5,
          mb: 1,
          px: 1.2,
          py: 0.75,
          minHeight: 40,
          maxHeight: 40,
          height: 40,
          flex: '0 0 auto',
          alignItems: 'center',
          justifyContent: 'flex-start',
          bgcolor: isHomeActive ? '#e8f0ff' : 'transparent',
          '&:hover': {
            bgcolor: '#f3f6fb',
          },
          '&.Mui-selected': {
            bgcolor: '#e8f0ff',
          },
        }}
      >
        <ListItemIcon sx={{ minWidth: 30, color: isHomeActive ? '#1d4ed8' : '#4b5563' }}>
          <HomeIcon fontSize="small" />
        </ListItemIcon>
        <ListItemText
          primary={homeRouteMeta.title}
          primaryTypographyProps={{
            fontWeight: 700,
            color: isHomeActive ? '#1d4ed8' : '#111827',
            fontSize: 14,
          }}
        />
      </ListItemButton>

      <Divider sx={{ borderColor: '#e5e7eb', mb: 1.2 }} />

      <Box
        sx={{
          flexGrow: 1,
          overflowY: 'auto',
          pr: 0.3,
          '& .MuiListItemButton-root': {
            color: '#111827',
          },
          '& .MuiListItemIcon-root': {
            color: '#6b7280',
          },
          '& .MuiTypography-root': {
            color: '#111827',
          },
        }}
      >
        {loading && (
          <Typography variant="body2" sx={{ px: 1, py: 1, color: '#d2e1ff' }}>
            Loading demo navigation...
          </Typography>
        )}

        {!loading && error && (
          <Alert severity="error" sx={{ bgcolor: '#fff3f3' }}>
            {error}
          </Alert>
        )}

        {!loading && !error && sections.length === 0 && (
          <Typography variant="body2" sx={{ px: 1, py: 1, color: '#d2e1ff' }}>
            No demo navigation available.
          </Typography>
        )}

        {!loading && !error && sections.length > 0 && (
          <SidebarNavigation sections={sections} activePath={activePath} onNavigate={onNavigate} />
        )}
      </Box>

    </Box>
  );
}

export function AppSidebar({ open, onClose, menus, loading, error, activePath, onNavigate }: AppSidebarProps) {
  const sections = useMemo(() => buildSidebarMenuSections(menus), [menus]);

  const drawerPaperSx = {
    width: APP_SIDEBAR_WIDTH,
    top: APP_HEADER_HEIGHT,
    height: `calc(100% - ${APP_HEADER_HEIGHT}px)`,
    borderRight: '1px solid #e5e7eb',
    boxSizing: 'border-box',
    overflow: 'hidden',
  } as const;

  return (
    <Drawer
      variant="temporary"
      anchor="left"
      open={open}
      onClose={onClose}
      ModalProps={{ keepMounted: true }}
      sx={{
        '& .MuiDrawer-paper': drawerPaperSx,
      }}
    >
      <SidebarContent
        sections={sections}
        activePath={activePath}
        loading={loading}
        error={error}
        onNavigate={onNavigate}
      />
    </Drawer>
  );
}
