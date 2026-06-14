import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import InsightsRoundedIcon from '@mui/icons-material/InsightsRounded';
import PsychologyRoundedIcon from '@mui/icons-material/PsychologyRounded';
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded';
import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import type { ReactNode } from 'react';

const DEMO_MODELS = [
  {
    id: 'DEMO-RUN-IF-001',
    name: 'Isolation Forest',
    type: 'Anomaly Detection',
    dataset: 'DEMO_DATASET_MANUFACTURING_AI',
    status: 'Completed',
    metricLabel: 'Anomaly rate',
    metricValue: '4.5%',
  },
  {
    id: 'DEMO-RUN-RF-001',
    name: 'Random Forest',
    type: 'Supervised Learning',
    dataset: 'DEMO_DATASET_MANUFACTURING_AI',
    status: 'Completed',
    metricLabel: 'Accuracy',
    metricValue: '94.0%',
  },
];

const DEMO_SIGNALS = [
  { label: 'furnace_temp', value: 'High influence' },
  { label: 'vibration_rms', value: 'Watch list' },
  { label: 'motor_current', value: 'Stable' },
];

function OverviewCard({
  title,
  value,
  helper,
  icon,
}: {
  title: string;
  value: string;
  helper: string;
  icon: ReactNode;
}) {
  return (
    <Card sx={{ height: '100%', borderRadius: 2 }}>
      <CardContent>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Box sx={{ color: 'primary.main', display: 'grid', placeItems: 'center' }}>{icon}</Box>
          <Box>
            <Typography variant="body2" color="text.secondary">
              {title}
            </Typography>
            <Typography variant="h5" fontWeight={800}>
              {value}
            </Typography>
          </Box>
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5 }}>
          {helper}
        </Typography>
      </CardContent>
    </Card>
  );
}

export function AiOverviewPage() {
  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4" fontWeight={800}>
          AI Overview
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 0.5 }}>
          Public demo summary for active model status, recent runs, and synthetic feature signals.
        </Typography>
      </Box>

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' }, gap: 2 }}>
        <Box>
          <OverviewCard
            title="Active models"
            value="2"
            helper="Demo anomaly and supervised workflows are available."
            icon={<PsychologyRoundedIcon />}
          />
        </Box>
        <Box>
          <OverviewCard
            title="Latest status"
            value="Completed"
            helper="Synthetic runs are ready for result review."
            icon={<CheckCircleRoundedIcon />}
          />
        </Box>
        <Box>
          <OverviewCard
            title="Demo dataset"
            value="Manufacturing AI"
            helper="All records use public-safe DEMO identifiers."
            icon={<InsightsRoundedIcon />}
          />
        </Box>
      </Box>

      <Card sx={{ borderRadius: 2 }}>
        <CardContent>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
            <Box>
              <Typography variant="h6" fontWeight={800}>
                Recent Demo Runs
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Model execution records shown here are synthetic.
              </Typography>
            </Box>
            <Chip color="success" label="Demo ready" />
          </Stack>

          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Run ID</TableCell>
                <TableCell>Model</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Dataset</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Metric</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {DEMO_MODELS.map((model) => (
                <TableRow key={model.id}>
                  <TableCell>{model.id}</TableCell>
                  <TableCell>{model.name}</TableCell>
                  <TableCell>{model.type}</TableCell>
                  <TableCell>{model.dataset}</TableCell>
                  <TableCell>
                    <Chip size="small" color="success" label={model.status} />
                  </TableCell>
                  <TableCell>
                    {model.metricLabel}: {model.metricValue}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '7fr 5fr' }, gap: 2 }}>
        <Box>
          <Card sx={{ height: '100%', borderRadius: 2 }}>
            <CardContent>
              <Typography variant="h6" fontWeight={800}>
                Signal Highlights
              </Typography>
              <Stack spacing={1.5} sx={{ mt: 2 }}>
                {DEMO_SIGNALS.map((signal) => (
                  <Stack
                    key={signal.label}
                    direction="row"
                    justifyContent="space-between"
                    sx={{ borderBottom: '1px solid #eef2f7', pb: 1 }}
                  >
                    <Typography fontWeight={700}>{signal.label}</Typography>
                    <Typography color="text.secondary">{signal.value}</Typography>
                  </Stack>
                ))}
              </Stack>
            </CardContent>
          </Card>
        </Box>
        <Box>
          <Alert severity="info" icon={<WarningAmberRoundedIcon />}>
            Load seed data for richer API-backed views, or continue using the built-in public demo fallback.
          </Alert>
        </Box>
      </Box>
    </Stack>
  );
}
