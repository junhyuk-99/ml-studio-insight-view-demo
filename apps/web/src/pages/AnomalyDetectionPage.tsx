import { Alert, Box, Card, CardContent, Chip, Stack, Typography } from '@mui/material';

export function AnomalyDetectionPage() {
  return (
    <Stack spacing={2.5}>
      <Box>
        <Typography variant="h4" sx={{ fontWeight: 800 }}>
          Anomaly Detection
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 0.75 }}>
          Demo view for reviewing model runs, anomaly status, and equipment-level health signals.
        </Typography>
      </Box>

      <Alert severity="info">
        Public demo data uses synthetic equipment IDs such as DEMO-MC-001 and demo model runs such as DEMO_RUN_001.
      </Alert>

      <Box
        sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: { xs: '1fr', md: 'repeat(3, minmax(0, 1fr))' },
        }}
      >
        <SummaryCard title="Current status" value="Demo Monitoring" tone="info" />
        <SummaryCard title="Latest score" value="0.87" tone="warning" />
        <SummaryCard title="Equipment" value="DEMO-MC-001" tone="success" />
      </Box>
    </Stack>
  );
}

function SummaryCard({ title, value, tone }: { title: string; value: string; tone: 'info' | 'success' | 'warning' }) {
  const color = tone === 'warning' ? 'warning' : tone === 'success' ? 'success' : 'info';

  return (
    <Card variant="outlined" sx={{ borderRadius: 1 }}>
      <CardContent>
        <Typography variant="overline" color="text.secondary">
          {title}
        </Typography>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1 }}>
          <Typography variant="h5" sx={{ fontWeight: 800 }}>
            {value}
          </Typography>
          <Chip size="small" color={color} label="demo" />
        </Stack>
      </CardContent>
    </Card>
  );
}
