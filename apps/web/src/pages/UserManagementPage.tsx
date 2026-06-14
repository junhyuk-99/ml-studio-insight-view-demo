import AddRoundedIcon from '@mui/icons-material/AddRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import EditRoundedIcon from '@mui/icons-material/EditRounded';
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Snackbar,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { userService } from '../services/userService';
import type { UserRole, UserUseflag } from '../types/auth';
import type { UserItem } from '../types/user';

type DialogMode = 'create' | 'edit' | null;

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

function normalizeSearchValue(value: string): string {
  return value.trim().toLowerCase();
}

export function UserManagementPage() {
  const [users, setUsers] = useState<UserItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searchText, setSearchText] = useState('');
  const [selectedEmpcode, setSelectedEmpcode] = useState<string | null>(null);

  const [dialogMode, setDialogMode] = useState<DialogMode>(null);
  const [empcode, setEmpcode] = useState('');
  const [empname, setEmpname] = useState('');
  const [emppass, setEmppass] = useState('');
  const [role, setRole] = useState<UserRole>('user');
  const [useflag, setUseflag] = useState<UserUseflag>('y');
  const [formError, setFormError] = useState<string | null>(null);
  const [formSubmitting, setFormSubmitting] = useState(false);

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [snackbar, setSnackbar] = useState<SnackbarState>(DEFAULT_SNACKBAR);

  const selectedUser = useMemo(
    () => users.find((item) => item.empcode === selectedEmpcode) ?? null,
    [selectedEmpcode, users],
  );

  const filteredUsers = useMemo(() => {
    const keyword = normalizeSearchValue(searchText);
    if (!keyword) {
      return users;
    }

    return users.filter((item) => {
      const code = normalizeSearchValue(item.empcode);
      const name = normalizeSearchValue(item.empname);
      return code.includes(keyword) || name.includes(keyword);
    });
  }, [searchText, users]);

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await userService.getUsers();
      setUsers(response);

      if (selectedEmpcode && !response.some((item) => item.empcode === selectedEmpcode)) {
        setSelectedEmpcode(null);
      }
    } catch (loadError: unknown) {
      setError(loadError instanceof Error ? loadError.message : '사용자 목록 조회에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, [selectedEmpcode]);

  useEffect(() => {
    void loadUsers();
  }, [loadUsers]);

  const openCreateDialog = () => {
    setDialogMode('create');
    setEmpcode('');
    setEmpname('');
    setEmppass('');
    setRole('user');
    setUseflag('y');
    setFormError(null);
  };

  const openEditDialog = () => {
    if (!selectedUser) {
      setSnackbar({
        open: true,
        message: '수정할 사용자를 먼저 선택하세요.',
        severity: 'error',
      });
      return;
    }

    setDialogMode('edit');
    setEmpcode(selectedUser.empcode);
    setEmpname(selectedUser.empname);
    setEmppass('');
    setRole(selectedUser.role);
    setUseflag(selectedUser.useflag);
    setFormError(null);
  };

  const closeFormDialog = () => {
    if (formSubmitting) {
      return;
    }
    setDialogMode(null);
    setFormError(null);
  };

  const validateForm = (): boolean => {
    if (!empcode.trim() && dialogMode === 'create') {
      setFormError('사번/아이디를 입력하세요.');
      return false;
    }

    if (!empname.trim()) {
      setFormError('이름을 입력하세요.');
      return false;
    }

    if (dialogMode === 'create') {
      if (!emppass) {
        setFormError('신규 등록 시 비밀번호는 필수입니다.');
        return false;
      }
      if (emppass.length < 4) {
        setFormError('비밀번호는 최소 4자 이상이어야 합니다.');
        return false;
      }
    }

    return true;
  };

  const submitForm = async () => {
    if (dialogMode === null) {
      return;
    }

    setFormError(null);
    if (!validateForm()) {
      return;
    }

    setFormSubmitting(true);

    try {
      if (dialogMode === 'create') {
        await userService.createUser({
          empcode: empcode.trim(),
          empname: empname.trim(),
          emppass,
          role,
          useflag,
        });

        setSnackbar({
          open: true,
          message: '사용자가 등록되었습니다.',
          severity: 'success',
        });
      } else {
        await userService.updateUser(empcode, {
          empname: empname.trim(),
          role,
          useflag,
        });

        setSnackbar({
          open: true,
          message: '사용자 정보가 수정되었습니다.',
          severity: 'success',
        });
      }

      setDialogMode(null);
      await loadUsers();
    } catch (submitError: unknown) {
      const message = submitError instanceof Error ? submitError.message : '저장에 실패했습니다.';
      setFormError(message);
      setSnackbar({
        open: true,
        message,
        severity: 'error',
      });
    } finally {
      setFormSubmitting(false);
    }
  };

  const requestDelete = () => {
    if (!selectedUser) {
      setSnackbar({
        open: true,
        message: '삭제할 사용자를 먼저 선택하세요.',
        severity: 'error',
      });
      return;
    }

    setDeleteDialogOpen(true);
  };

  const confirmDelete = async () => {
    if (!selectedUser) {
      setDeleteDialogOpen(false);
      return;
    }

    try {
      await userService.deleteUser(selectedUser.empcode);
      setDeleteDialogOpen(false);
      setSelectedEmpcode(null);
      setSnackbar({
        open: true,
        message: '사용자가 삭제되었습니다.',
        severity: 'success',
      });
      await loadUsers();
    } catch (deleteError: unknown) {
      const message = deleteError instanceof Error ? deleteError.message : '삭제에 실패했습니다.';
      setSnackbar({
        open: true,
        message,
        severity: 'error',
      });
    }
  };

  return (
    <Stack spacing={2.2}>
      <Card>
        <CardContent>
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            spacing={1.2}
            alignItems={{ xs: 'stretch', md: 'center' }}
            justifyContent="space-between"
          >
            <TextField
              label="사용자 검색"
              placeholder="사번/아이디 또는 이름"
              value={searchText}
              onChange={(event) => setSearchText(event.target.value)}
              sx={{ minWidth: { xs: '100%', md: 320 } }}
            />

            <Stack direction="row" spacing={1}>
              <Button variant="outlined" startIcon={<RefreshRoundedIcon />} onClick={() => void loadUsers()}>
                새로고침
              </Button>
              <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={openCreateDialog}>
                신규 등록
              </Button>
              <Button variant="outlined" startIcon={<EditRoundedIcon />} onClick={openEditDialog}>
                수정
              </Button>
              <Button color="error" variant="outlined" startIcon={<DeleteOutlineRoundedIcon />} onClick={requestDelete}>
                삭제
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 1.5 }}>
            사원 정보 목록
          </Typography>

          {error && (
            <Alert severity="error" sx={{ mb: 1.5 }}>
              {error}
            </Alert>
          )}

          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>사번/아이디</TableCell>
                  <TableCell>이름</TableCell>
                  <TableCell>권한</TableCell>
                  <TableCell>사용여부</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredUsers.map((item) => {
                  const selected = item.empcode === selectedEmpcode;

                  return (
                    <TableRow
                      key={item.empcode}
                      hover
                      selected={selected}
                      onClick={() => setSelectedEmpcode(item.empcode)}
                      sx={{ cursor: 'pointer' }}
                    >
                      <TableCell>{item.empcode}</TableCell>
                      <TableCell>{item.empname}</TableCell>
                      <TableCell>{item.role}</TableCell>
                      <TableCell>{item.useflag}</TableCell>
                    </TableRow>
                  );
                })}

                {!loading && filteredUsers.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} align="center">
                      조회 결과가 없습니다.
                    </TableCell>
                  </TableRow>
                )}

                {loading && (
                  <TableRow>
                    <TableCell colSpan={4} align="center">
                      사용자 목록을 불러오는 중입니다.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      <Dialog open={dialogMode !== null} onClose={closeFormDialog} maxWidth="sm" fullWidth>
        <DialogTitle>{dialogMode === 'create' ? '사용자 신규 등록' : '사용자 수정'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              label="사번/아이디"
              value={empcode}
              onChange={(event) => setEmpcode(event.target.value)}
              disabled={dialogMode === 'edit'}
              required
              fullWidth
            />
            <TextField
              label="이름"
              value={empname}
              onChange={(event) => setEmpname(event.target.value)}
              required
              fullWidth
            />

            {dialogMode === 'create' && (
              <TextField
                label="비밀번호"
                type="password"
                value={emppass}
                onChange={(event) => setEmppass(event.target.value)}
                required
                fullWidth
                helperText="최소 4자 이상"
              />
            )}

            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
              <FormControl fullWidth required>
                <InputLabel id="user-role-label">권한</InputLabel>
                <Select
                  labelId="user-role-label"
                  label="권한"
                  value={role}
                  onChange={(event) => setRole(event.target.value as UserRole)}
                >
                  <MenuItem value="admin">admin</MenuItem>
                  <MenuItem value="user">user</MenuItem>
                </Select>
              </FormControl>

              <FormControl fullWidth required>
                <InputLabel id="user-useflag-label">사용여부</InputLabel>
                <Select
                  labelId="user-useflag-label"
                  label="사용여부"
                  value={useflag}
                  onChange={(event) => setUseflag(event.target.value as UserUseflag)}
                >
                  <MenuItem value="y">y</MenuItem>
                  <MenuItem value="n">n</MenuItem>
                </Select>
              </FormControl>
            </Box>

            {formError && <Alert severity="error">{formError}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeFormDialog} disabled={formSubmitting}>
            취소
          </Button>
          <Button variant="contained" onClick={() => void submitForm()} disabled={formSubmitting}>
            저장
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>사용자 삭제</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            {selectedUser
              ? `Delete account ${selectedUser.empcode} (${selectedUser.empname})?`
              : '선택된 사용자가 없습니다.'}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>취소</Button>
          <Button color="error" variant="contained" onClick={() => void confirmDelete()}>
            삭제
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={3200}
        onClose={() => setSnackbar(DEFAULT_SNACKBAR)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          severity={snackbar.severity}
          variant="filled"
          onClose={() => setSnackbar(DEFAULT_SNACKBAR)}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Stack>
  );
}
