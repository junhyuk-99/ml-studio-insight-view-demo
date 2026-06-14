import PersonOutlineRoundedIcon from '@mui/icons-material/PersonOutlineRounded';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import LoginIcon from '@mui/icons-material/Login';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  InputAdornment,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { alpha, keyframes } from '@mui/material/styles';
import { FormEvent, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';

const floatBackground = keyframes`
  0% {
    transform: scale(1) translate3d(0, 0, 0);
  }
  50% {
    transform: scale(1.03) translate3d(-10px, 8px, 0);
  }
  100% {
    transform: scale(1) translate3d(0, 0, 0);
  }
`;

const glowPulse = keyframes`
  0% {
    opacity: 0.55;
  }
  50% {
    opacity: 0.9;
  }
  100% {
    opacity: 0.55;
  }
`;

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { signIn, signInDemo } = useAuth();

  const [empcode, setEmpcode] = useState('');
  const [emppass, setEmppass] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const navigateAfterLogin = () => {
    const from = location.state && typeof location.state === 'object' && 'from' in location.state
      ? (location.state.from as { pathname?: string } | null)
      : null;

    navigate(from?.pathname ?? '/', { replace: true });
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setErrorMessage(null);

    try {
      await signIn(empcode, emppass);
      navigateAfterLogin();
    } catch (error: unknown) {
      setErrorMessage(error instanceof Error ? error.message : '로그인에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDemoLogin = () => {
    setSubmitting(false);
    setErrorMessage(null);
    signInDemo();
    navigateAfterLogin();
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        position: 'relative',
        overflow: 'hidden',
        display: 'grid',
        placeItems: 'center',
        px: 2,
        background: 'linear-gradient(180deg, #f7f9ff 0%, #edf4ff 100%)',
      }}
    >
      {/* 배경 */}
      <Box
        sx={{
          position: 'absolute',
          inset: '-6%',
          animation: `${floatBackground} 16s ease-in-out infinite`,
          background: `
            radial-gradient(circle at 18% 62%, rgba(193, 169, 255, 0.22) 0%, transparent 26%),
            radial-gradient(circle at 72% 14%, rgba(216, 164, 255, 0.20) 0%, transparent 18%),
            radial-gradient(circle at 88% 18%, rgba(122, 176, 255, 0.24) 0%, transparent 28%),
            radial-gradient(circle at 82% 72%, rgba(173, 220, 255, 0.14) 0%, transparent 22%),
            linear-gradient(135deg, #f7f8ff 0%, #eef2ff 42%, #dde9ff 74%, #d4ebff 100%)
          `,
        }}
      />

      {/* 좌하단 메시 네트워크 */}
      <Box
        sx={{
          position: 'absolute',
          left: '-8%',
          bottom: '-6%',
          width: '42%',
          height: '34%',
          opacity: 0.55,
          background: `
            radial-gradient(circle at 20% 80%, rgba(146, 108, 255, 0.35) 0%, transparent 50%),
            linear-gradient(135deg, rgba(255,255,255,0.45), rgba(255,255,255,0.02))
          `,
          clipPath: 'polygon(0% 100%, 0% 55%, 12% 48%, 26% 60%, 38% 44%, 52% 66%, 68% 48%, 82% 72%, 100% 58%, 100% 100%)',
          filter: 'blur(4px)',
        }}
      />

      {/* 우하단 메시 네트워크 */}
      <Box
        sx={{
          position: 'absolute',
          right: '-4%',
          bottom: '-6%',
          width: '38%',
          height: '42%',
          opacity: 0.5,
          background: `
            radial-gradient(circle at 70% 65%, rgba(102, 168, 255, 0.28) 0%, transparent 45%),
            linear-gradient(135deg, rgba(255,255,255,0.42), rgba(255,255,255,0.02))
          `,
          clipPath: 'polygon(0% 78%, 14% 60%, 28% 68%, 42% 42%, 54% 58%, 66% 34%, 80% 52%, 100% 28%, 100% 100%, 0% 100%)',
          filter: 'blur(4px)',
        }}
      />

      {/* 발광 포인트 */}
      <Box
        sx={{
          position: 'absolute',
          width: 260,
          height: 260,
          top: 110,
          right: '28%',
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(207, 124, 255, 0.34) 0%, rgba(207, 124, 255, 0.08) 42%, transparent 72%)',
          filter: 'blur(26px)',
          animation: `${glowPulse} 7s ease-in-out infinite`,
        }}
      />
      <Box
        sx={{
          position: 'absolute',
          width: 300,
          height: 300,
          left: -40,
          bottom: 80,
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(146, 108, 255, 0.20) 0%, rgba(146, 108, 255, 0.05) 44%, transparent 74%)',
          filter: 'blur(24px)',
          animation: `${glowPulse} 8s ease-in-out infinite`,
        }}
      />

      {/* 좌하단 도트 웨이브 */}
      <Box
        sx={{
          position: 'absolute',
          left: '-6%',
          bottom: '-8%',
          width: '42%',
          height: '36%',
          opacity: 0.95,
          backgroundImage: `
            radial-gradient(circle, rgba(255,255,255,0.95) 0 1.2px, transparent 1.8px)
          `,
          backgroundSize: '14px 14px',
          maskImage: `
            radial-gradient(90% 80% at 50% 100%, rgba(0,0,0,1) 0%, rgba(0,0,0,0.92) 36%, rgba(0,0,0,0.24) 72%, transparent 100%)
          `,
          transform: 'perspective(700px) rotateX(66deg) rotateZ(-12deg)',
          filter: 'blur(0.2px)',
        }}
      />

      {/* 우하단 도트 웨이브 */}
      <Box
        sx={{
          position: 'absolute',
          right: '-4%',
          bottom: '-7%',
          width: '38%',
          height: '34%',
          opacity: 0.95,
          backgroundImage: `
            radial-gradient(circle, rgba(255,255,255,0.95) 0 1.2px, transparent 1.9px)
          `,
          backgroundSize: '13px 13px',
          maskImage: `
            radial-gradient(95% 85% at 50% 100%, rgba(0,0,0,1) 0%, rgba(0,0,0,0.88) 34%, rgba(0,0,0,0.20) 72%, transparent 100%)
          `,
          transform: 'perspective(700px) rotateX(67deg) rotateZ(12deg)',
          filter: 'blur(0.2px)',
        }}
      />

      {/* 좌측 연결선 네트워크 */}
      <Box
        sx={{
          position: 'absolute',
          left: '-2%',
          bottom: '12%',
          width: '34%',
          height: '34%',
          opacity: 0.38,
          background: `
            linear-gradient(62deg, transparent 0 48%, rgba(255,255,255,0.78) 49%, transparent 50%) 0 0 / 150px 120px,
            linear-gradient(128deg, transparent 0 48%, rgba(255,255,255,0.52) 49%, transparent 50%) 0 0 / 150px 120px,
            linear-gradient(12deg, transparent 0 48%, rgba(255,255,255,0.36) 49%, transparent 50%) 0 0 / 150px 120px
          `,
          clipPath: 'polygon(0% 100%, 0% 18%, 18% 8%, 34% 24%, 48% 10%, 62% 34%, 78% 20%, 100% 42%, 100% 100%)',
          filter: 'blur(0.4px)',
        }}
      />

      {/* 우측 연결선 네트워크 */}
      <Box
        sx={{
          position: 'absolute',
          right: '-2%',
          bottom: '10%',
          width: '34%',
          height: '36%',
          opacity: 0.42,
          background: `
            linear-gradient(58deg, transparent 0 48%, rgba(255,255,255,0.82) 49%, transparent 50%) 0 0 / 160px 130px,
            linear-gradient(122deg, transparent 0 48%, rgba(255,255,255,0.58) 49%, transparent 50%) 0 0 / 160px 130px,
            linear-gradient(8deg, transparent 0 48%, rgba(255,255,255,0.34) 49%, transparent 50%) 0 0 / 160px 130px
          `,
          clipPath: 'polygon(0% 46%, 16% 24%, 32% 40%, 50% 14%, 66% 30%, 82% 10%, 100% 22%, 100% 100%, 0% 100%)',
          filter: 'blur(0.4px)',
        }}
      />

      {/* 좌측 네트워크 노드 */}
      {[
        { left: '1%', bottom: '43%' },
        { left: '8%', bottom: '31%' },
        { left: '16%', bottom: '50%' },
        { left: '24%', bottom: '36%' },
        { left: '30%', bottom: '23%' },
      ].map((node, index) => (
        <Box
          key={`left-node-${index}`}
          sx={{
            position: 'absolute',
            width: 9,
            height: 9,
            borderRadius: '50%',
            background: 'rgba(255,255,255,0.96)',
            boxShadow: '0 0 12px rgba(255,255,255,0.75)',
            opacity: 0.75,
            ...node,
          }}
        />
      ))}

      {/* 우측 네트워크 노드 */}
      {[
        { right: '3%', bottom: '48%' },
        { right: '11%', bottom: '38%' },
        { right: '18%', bottom: '26%' },
        { right: '25%', bottom: '43%' },
        { right: '20%', bottom: '57%' },
      ].map((node, index) => (
        <Box
          key={`right-node-${index}`}
          sx={{
            position: 'absolute',
            width: 10,
            height: 10,
            borderRadius: '50%',
            background: 'rgba(255,255,255,0.96)',
            boxShadow: '0 0 14px rgba(255,255,255,0.75)',
            opacity: 0.8,
            ...node,
          }}
        />
      ))}

      {/* 로그인 카드 */}
      <Card
        sx={{
          position: 'relative',
          zIndex: 2,
          width: '100%',
          maxWidth: 500,
          borderRadius: '42px',
          background: 'linear-gradient(180deg, rgba(255,255,255,0.62) 0%, rgba(255,255,255,0.48) 100%)',
          border: '1px solid rgba(255,255,255,0.65)',
          boxShadow: '0 24px 60px rgba(140, 165, 210, 0.20)',
          backdropFilter: 'blur(18px)',
        }}
      >
        <CardContent sx={{ px: 6, py: 5 }}>
          <Box sx={{ textAlign: 'center', mb: 4 }}>
            <Box
              sx={{
                width: 118,
                height: 118,
                mx: 'auto',
                mb: 2.5,
                borderRadius: '50%',
                display: 'grid',
                placeItems: 'center',
                background: 'rgba(255,255,255,0.92)',
                boxShadow: '0 8px 24px rgba(120, 146, 197, 0.16)',
              }}
            >
              <Box
                component="img"
                src="/demo-logo.svg"
                alt="ML Studio Demo"
                sx={{
                  width: 78,
                  height: 78,
                  objectFit: 'contain',
                  borderRadius: '50%',
                }}
              />
            </Box>

            <Typography
              sx={{
                color: '#4b4bb7',
                fontSize: 28,
                fontWeight: 800,
                lineHeight: 1.2,
                mb: 1,
              }}
            >
              AI Insight View
            </Typography>

            <Typography
              sx={{
                color: 'rgba(82, 95, 152, 0.82)',
                fontSize: 14,
                fontWeight: 500,
              }}
            >
              AI Monitoring & Analytics Platform
            </Typography>
          </Box>

          <Stack component="form" spacing={3} onSubmit={handleSubmit}>
            <TextField
              variant="standard"
              placeholder="아이디"
              value={empcode}
              onChange={(event) => setEmpcode(event.target.value)}
              autoComplete="username"
              required
              fullWidth
              InputProps={{
                disableUnderline: false,
                startAdornment: (
                  <InputAdornment position="start">
                    <PersonOutlineRoundedIcon sx={{ color: '#7c6cff', fontSize: 25 }} />
                  </InputAdornment>
                ),
                sx: {
                  color: '#66708f',
                  fontSize: 16,
                  pb: 0.8,
                  '&:before': {
                    borderBottom: '2px solid rgba(144, 145, 233, 0.24)',
                  },
                  '&:hover:not(.Mui-disabled):before': {
                    borderBottom: '2px solid rgba(124, 108, 255, 0.45)',
                  },
                  '&:after': {
                    borderBottom: '2px solid rgba(124, 108, 255, 0.72)',
                  },
                },
              }}
              inputProps={{
                sx: {
                  color: '#7680a3',
                  '::placeholder': {
                    color: '#9aa4c8',
                    opacity: 1,
                  },
                },
              }}
            />

            <TextField
              type="password"
              variant="standard"
              placeholder="비밀번호"
              value={emppass}
              onChange={(event) => setEmppass(event.target.value)}
              autoComplete="current-password"
              required
              fullWidth
              InputProps={{
                disableUnderline: false,
                startAdornment: (
                  <InputAdornment position="start">
                    <LockOutlinedIcon sx={{ color: '#7c6cff', fontSize: 24 }} />
                  </InputAdornment>
                ),
                sx: {
                  color: '#66708f',
                  fontSize: 16,
                  pb: 0.8,
                  '&:before': {
                    borderBottom: '2px solid rgba(144, 145, 233, 0.24)',
                  },
                  '&:hover:not(.Mui-disabled):before': {
                    borderBottom: '2px solid rgba(124, 108, 255, 0.45)',
                  },
                  '&:after': {
                    borderBottom: '2px solid rgba(124, 108, 255, 0.72)',
                  },
                },
              }}
              inputProps={{
                sx: {
                  color: '#7680a3',
                  '::placeholder': {
                    color: '#9aa4c8',
                    opacity: 1,
                  },
                },
              }}
            />

            {errorMessage && (
              <Alert
                severity="error"
                sx={{
                  borderRadius: 2.5,
                  backgroundColor: alpha('#fff5f7', 0.95),
                }}
              >
                {errorMessage}
              </Alert>
            )}

            <Typography
              variant="body2"
              sx={{
                color: 'rgba(82, 95, 152, 0.82)',
                fontWeight: 600,
                textAlign: 'center',
              }}
            >
              Demo account: admin / admin
            </Typography>

            <Button
              type="submit"
              variant="contained"
              disableElevation
              startIcon={<LoginIcon />}
              disabled={submitting}
              sx={{
                height: 56,
                borderRadius: '14px',
                fontWeight: 800,
                fontSize: 18,
                color: '#fff',
                background: 'linear-gradient(90deg, #3ca0ff 0%, #9252ff 100%)',
                boxShadow: '0 14px 26px rgba(116, 116, 255, 0.24)',
                '&:hover': {
                  background: 'linear-gradient(90deg, #3294fb 0%, #8748fb 100%)',
                  boxShadow: '0 16px 30px rgba(116, 116, 255, 0.30)',
                },
                '&.Mui-disabled': {
                  color: 'rgba(255,255,255,0.7)',
                  background: 'linear-gradient(90deg, rgba(60,160,255,0.55) 0%, rgba(146,82,255,0.55) 100%)',
                },
              }}
            >
              {submitting ? '로그인 중...' : '로그인'}
            </Button>

            <Button
              type="button"
              variant="outlined"
              disableElevation
              onClick={handleDemoLogin}
              disabled={submitting}
              sx={{
                height: 48,
                borderRadius: '14px',
                fontWeight: 800,
                color: '#5f57d8',
                borderColor: 'rgba(124, 108, 255, 0.36)',
                backgroundColor: 'rgba(255,255,255,0.62)',
                '&:hover': {
                  borderColor: 'rgba(124, 108, 255, 0.62)',
                  backgroundColor: 'rgba(255,255,255,0.82)',
                },
              }}
            >
              Demo Login
            </Button>
          </Stack>
        </CardContent>
      </Card>

      <Typography
        sx={{
          position: 'absolute',
          bottom: 22,
          left: '50%',
          transform: 'translateX(-50%)',
          color: 'rgba(122, 131, 173, 0.72)',
          fontSize: 13,
          fontWeight: 500,
          zIndex: 2,
        }}
      >
        © W&E Korea. All rights reserved.
      </Typography>
    </Box>
  );
}
