import { Alert, Box, Card, CardContent, Chip, Stack, Typography } from '@mui/material';

const DEMO_ALERTS = [
  { id: 'DEMO-ALM-001', equipment: 'DEMO-MC-001', field: 'temperature', severity: 'WARN', status: 'OPEN' },
  { id: 'DEMO-ALM-002', equipment: 'DEMO-MC-002', field: 'pressure', severity: 'INFO', status: 'ACK' },
];

export function ThresholdAlertPage() {
  return (
    <Stack spacing={2.5}>
      <Box>
        <Typography variant="h4" sx={{ fontWeight: 800 }}>
          Threshold Alerts
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 0.75 }}>
          Demo view for monitoring synthetic threshold events and acknowledgement status.
        </Typography>
      </Box>

      <Alert severity="info">
        Alert rows are illustrative only and do not contain real facility, equipment, production, or defect data.
      </Alert>

      <Box sx={{ display: 'grid', gap: 2 }}>
        {DEMO_ALERTS.map((alert) => (
          <Card key={alert.id} variant="outlined" sx={{ borderRadius: 1 }}>
            <CardContent>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} alignItems={{ sm: 'center' }}>
                <Typography sx={{ fontWeight: 800, minWidth: 140 }}>{alert.id}</Typography>
                <Typography sx={{ flex: 1 }}>
                  {alert.equipment} / {alert.field}
                </Typography>
                <Chip label={alert.severity} color={alert.severity === 'WARN' ? 'warning' : 'info'} />
                <Chip label={alert.status} variant="outlined" />
              </Stack>
            </CardContent>
          </Card>
        ))}
      </Box>
    </Stack>
  );
}
