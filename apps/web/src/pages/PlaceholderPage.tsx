import { Card, CardContent, Typography } from '@mui/material';
import { useLocation } from 'react-router-dom';

export function PlaceholderPage() {
  const location = useLocation();

  return (
    <Card>
      <CardContent>
        <Typography variant="h5" sx={{ mb: 1 }}>
          준비 중인 화면
        </Typography>
        <Typography variant="body2" color="text.secondary">
          현재 경로: {location.pathname}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          메뉴 데이터의 pgmpath를 통해 라우팅은 연결되며, 실제 화면은 이후 모듈별로 확장하면 됩니다.
        </Typography>
      </CardContent>
    </Card>
  );
}

