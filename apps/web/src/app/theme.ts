import { createTheme } from '@mui/material/styles';

export const appTheme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#1660cf',
    },
    secondary: {
      main: '#2b8f6d',
    },
    background: {
      default: '#f3f7ff',
      paper: '#ffffff',
    },
  },
  shape: {
    borderRadius: 12,
  },
  typography: {
    fontFamily: "'Pretendard', 'Noto Sans KR', 'Segoe UI', sans-serif",
    h4: {
      fontWeight: 700,
    },
    h6: {
      fontWeight: 700,
    },
  },
});

