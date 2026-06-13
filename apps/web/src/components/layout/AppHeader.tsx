import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import LockResetOutlinedIcon from '@mui/icons-material/LockResetOutlined';
import LogoutIcon from '@mui/icons-material/Logout';
import MenuIcon from '@mui/icons-material/Menu';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import {
  Alert,
  AppBar,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Snackbar,
  Stack,
  TextField,
  Toolbar,
  Typography,
} from '@mui/material';
import { useState, type MouseEvent } from 'react';
import { userService } from '../../services/userService';
import type { AuthUser } from '../../types/auth';
import { APP_HEADER_HEIGHT } from './layoutConstants';

type AppHeaderProps = {
  user: AuthUser;
  onMenuClick: () => void;
  onLogout: () => void;
};

type SnackbarState = {
  open: boolean;
  message: string;
  severity: 'success' | 'error';
};

const DEFAULT_SNACKBAR: SnackbarState = {
  open: false,
  message: '',
  severity: 'success',
};

function getRoleLabel(role: AuthUser['role']): string {
  return role === 'admin' ? '관리자' : '일반사용자';
}

export function AppHeader({ user, onMenuClick, onLogout }: AppHeaderProps) {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [profileDialogOpen, setProfileDialogOpen] = useState(false);
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordSubmitting, setPasswordSubmitting] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [snackbar, setSnackbar] = useState<SnackbarState>(DEFAULT_SNACKBAR);

  const nowLabel = new Date().toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
    timeZone: 'Asia/Seoul',
  });

  const menuOpen = Boolean(anchorEl);

  const handleOpenMenu = (event: MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleCloseMenu = () => {
    setAnchorEl(null);
  };

  const handleOpenProfile = () => {
    handleCloseMenu();
    setProfileDialogOpen(true);
  };

  const handleOpenPasswordDialog = () => {
    handleCloseMenu();
    setPasswordError(null);
    setPasswordDialogOpen(true);
  };

  const resetPasswordDialogState = () => {
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setPasswordError(null);
  };

  const handleClosePasswordDialog = () => {
    if (passwordSubmitting) {
      return;
    }
    setPasswordDialogOpen(false);
    resetPasswordDialogState();
  };

  const handleChangePassword = async () => {
    setPasswordError(null);

    if (!currentPassword.trim()) {
      setPasswordError('현재 비밀번호를 입력해 주세요.');
      return;
    }

    if (!newPassword.trim()) {
      setPasswordError('새 비밀번호를 입력해 주세요.');
      return;
    }

    if (newPassword.length < 4) {
      setPasswordError('새 비밀번호는 최소 4자 이상이어야 합니다.');
      return;
    }

    if (newPassword !== confirmPassword) {
      setPasswordError('새 비밀번호 확인이 일치하지 않습니다.');
      return;
    }

    setPasswordSubmitting(true);

    try {
      await userService.changePassword(user.empcode, {
        currentPassword,
        newPassword,
      });

      setPasswordDialogOpen(false);
      resetPasswordDialogState();
      setSnackbar({
        open: true,
        message: '비밀번호가 변경되었습니다.',
        severity: 'success',
      });
    } catch (error: unknown) {
      setPasswordError(error instanceof Error ? error.message : '비밀번호 변경에 실패했습니다.');
      setSnackbar({
        open: true,
        message: '비밀번호 변경에 실패했습니다.',
        severity: 'error',
      });
    } finally {
      setPasswordSubmitting(false);
    }
  };

  return (
    <>
      <AppBar
        position="fixed"
        color="inherit"
        elevation={1}
        sx={{
          borderBottom: '1px solid #d8e1f0',
          backgroundImage: 'linear-gradient(90deg, #ffffff 0%, #f4f8ff 100%)',
        }}
      >
        <Toolbar sx={{ minHeight: APP_HEADER_HEIGHT, gap: 1.5 }}>
          <IconButton edge="start" color="primary" onClick={onMenuClick}>
            <MenuIcon />
          </IconButton>

          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            AI Insight View
          </Typography>

          <Typography variant="body2" color="text.secondary" sx={{ display: { xs: 'none', sm: 'block' } }}>
            {nowLabel} (KST)
          </Typography>

          <Button
            color="inherit"
            onClick={handleOpenMenu}
            startIcon={<PersonOutlineIcon fontSize="small" />}
            sx={{
              ml: 1,
              textTransform: 'none',
              borderRadius: 2,
              px: 1.5,
            }}
          >
            <Box sx={{ textAlign: 'left' }}>
              <Typography variant="subtitle2" lineHeight={1.2}>
                {getRoleLabel(user.role)} {user.empname}
              </Typography>
              <Typography variant="caption" color="text.secondary" lineHeight={1.1}>
                {user.empcode}
              </Typography>
            </Box>
          </Button>

          <Menu anchorEl={anchorEl} open={menuOpen} onClose={handleCloseMenu}>
            <MenuItem onClick={handleOpenProfile}>
              <ListItemIcon>
                <InfoOutlinedIcon fontSize="small" />
              </ListItemIcon>
              <ListItemText primary="내 정보 보기" />
            </MenuItem>
            <MenuItem onClick={handleOpenPasswordDialog}>
              <ListItemIcon>
                <LockResetOutlinedIcon fontSize="small" />
              </ListItemIcon>
              <ListItemText primary="비밀번호 변경" />
            </MenuItem>
            <MenuItem
              onClick={() => {
                handleCloseMenu();
                onLogout();
              }}
            >
              <ListItemIcon>
                <LogoutIcon fontSize="small" />
              </ListItemIcon>
              <ListItemText primary="로그아웃" />
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      <Dialog open={profileDialogOpen} onClose={() => setProfileDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>내 정보</DialogTitle>
        <DialogContent>
          <Stack spacing={1.5} sx={{ pt: 0.5 }}>
            <TextField label="사번/아이디" value={user.empcode} InputProps={{ readOnly: true }} fullWidth />
            <TextField label="이름" value={user.empname} InputProps={{ readOnly: true }} fullWidth />
            <TextField label="권한" value={user.role} InputProps={{ readOnly: true }} fullWidth />
            <TextField label="사용여부" value={user.useflag} InputProps={{ readOnly: true }} fullWidth />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setProfileDialogOpen(false)}>닫기</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={passwordDialogOpen} onClose={handleClosePasswordDialog} maxWidth="xs" fullWidth>
        <DialogTitle>비밀번호 변경</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <TextField
              label="현재 비밀번호"
              type="password"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              autoComplete="current-password"
              fullWidth
            />
            <TextField
              label="새 비밀번호"
              type="password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              autoComplete="new-password"
              fullWidth
              helperText="최소 4자 이상"
            />
            <TextField
              label="새 비밀번호 확인"
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              autoComplete="new-password"
              fullWidth
            />
            {passwordError && <Alert severity="error">{passwordError}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClosePasswordDialog} disabled={passwordSubmitting}>
            취소
          </Button>
          <Button variant="contained" onClick={handleChangePassword} disabled={passwordSubmitting}>
            변경
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={3000}
        onClose={() => setSnackbar(DEFAULT_SNACKBAR)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity={snackbar.severity} variant="filled" onClose={() => setSnackbar(DEFAULT_SNACKBAR)}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </>
  );
}
