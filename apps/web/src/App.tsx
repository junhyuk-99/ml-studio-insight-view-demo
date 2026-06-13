import { CssBaseline, ThemeProvider } from '@mui/material';
import { AuthProvider } from './store/AuthContext';
import { AppRouter } from './routes/AppRouter';
import { appTheme } from './app/theme';
import { SelectedAlgorithmProvider } from './store/SelectedAlgorithmContext';

export default function App() {
  return (
    <ThemeProvider theme={appTheme}>
      <CssBaseline />
      <AuthProvider>
        <SelectedAlgorithmProvider>
          <AppRouter />
        </SelectedAlgorithmProvider>
      </AuthProvider>
    </ThemeProvider>
  );
}

