import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { defaultAlgorithmDatasetKey } from '../constants/algorithmDatasetPolicy';
import { modelTrainService } from '../services/modelTrainService';
import type { AnomalyResultPoint, AnomalyResultViewData, AnomalyRunOption } from '../types/modelTrain';

const DEMO_DATASET_KEY = defaultAlgorithmDatasetKey();

function formatNumber(value: number | null | undefined, digits = 2): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return '-';
  }
  return value.toFixed(digits);
}

function formatTime(value: string | null | undefined): string {
  if (!value) {
    return '-';
  }
  const parsed = Date.parse(value);
  if (!Number.isFinite(parsed)) {
    return value;
  }
  return new Date(parsed).toISOString().replace('T', ' ').replace('.000Z', 'Z');
}

function statusColor(status: string | null | undefined): 'default' | 'success' | 'warning' | 'error' {
  const normalized = status?.trim().toUpperCase();
  if (normalized === 'CRITICAL') {
    return 'error';
  }
  if (normalized === 'WARNING') {
    return 'warning';
  }
  if (normalized === 'NORMAL') {
    return 'success';
  }
  return 'default';
}

function buildStatusCounts(rows: AnomalyResultPoint[]): Array<{ status: string; count: number }> {
  const counts = new Map<string, number>();
  rows.forEach((row) => {
    const status = row.status?.trim().toUpperCase() || 'UNKNOWN';
    counts.set(status, (counts.get(status) ?? 0) + 1);
  });
  return Array.from(counts.entries()).map(([status, count]) => ({ status, count }));
}

export function AnomalyDetectionPage() {
  const [runs, setRuns] = useState<AnomalyRunOption[]>([]);
  const [selectedRunId, setSelectedRunId] = useState('');
  const [resultView, setResultView] = useState<AnomalyResultViewData | null>(null);
  const [loadingRuns, setLoadingRuns] = useState(true);
  const [loadingResults, setLoadingResults] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoadingRuns(true);
    setErrorMessage(null);

    modelTrainService
      .getAnomalyRunOptions({
        datasetKey: DEMO_DATASET_KEY,
        includeNonSuccess: true,
        limit: 20,
      })
      .then((data) => {
        if (!active) {
          return;
        }
        const nextRuns = data.runs ?? [];
        setRuns(nextRuns);
        setSelectedRunId(data.latest_success_run_id ?? data.latest_run_id ?? nextRuns[0]?.run_id ?? '');
      })
      .catch((error: unknown) => {
        if (!active) {
          return;
        }
        setErrorMessage(error instanceof Error ? error.message : 'Failed to load synthetic demo runs.');
      })
      .finally(() => {
        if (active) {
          setLoadingRuns(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!selectedRunId) {
      setResultView(null);
      return;
    }

    let active = true;
    setLoadingResults(true);
    setErrorMessage(null);

    modelTrainService
      .getAnomalyResultView({
        runId: selectedRunId,
        datasetKey: DEMO_DATASET_KEY,
        limit: 100,
      })
      .then((data) => {
        if (active) {
          setResultView(data);
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setErrorMessage(error instanceof Error ? error.message : 'Failed to load synthetic anomaly results.');
        }
      })
      .finally(() => {
        if (active) {
          setLoadingResults(false);
        }
      });

    return () => {
      active = false;
    };
  }, [selectedRunId]);

  const resultRows = resultView?.anomaly_results ?? [];
  const selectedRun = resultView?.run ?? runs.find((run) => run.run_id === selectedRunId) ?? null;
  const statusCounts = useMemo(() => buildStatusCounts(resultRows), [resultRows]);
  const maxStatusCount = Math.max(1, ...statusCounts.map((item) => item.count));

  return (
    <Stack spacing={2.5}>
      <Box>
        <Typography variant="h4" sx={{ fontWeight: 800 }}>
          Anomaly Detection
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 0.75 }}>
          Synthetic demo anomaly result table for DEMO_DATASET_MANUFACTURING_AI.
        </Typography>
      </Box>

      <Alert severity="info">
        Synthetic demo data only. Results are loaded from thisanomalyresult when seeded, or from a Synthetic demo fallback sample.
      </Alert>

      {errorMessage && <Alert severity="warning">{errorMessage}</Alert>}

      <Card variant="outlined" sx={{ borderRadius: 1 }}>
        <CardContent>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ xs: 'stretch', md: 'center' }}>
            <FormControl sx={{ minWidth: { xs: '100%', md: 320 } }} size="small">
              <InputLabel id="demo-run-selector-label">Run selector</InputLabel>
              <Select
                labelId="demo-run-selector-label"
                label="Run selector"
                value={selectedRunId}
                onChange={(event) => setSelectedRunId(event.target.value)}
                disabled={loadingRuns}
              >
                {runs.map((run) => (
                  <MenuItem key={run.run_id} value={run.run_id}>
                    {run.run_id} / {run.algo_name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            {loadingRuns ? (
              <CircularProgress size={24} />
            ) : (
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={`dataset: ${selectedRun?.dataset_key ?? DEMO_DATASET_KEY}`} size="small" />
                <Chip label={`algorithm: ${selectedRun?.algo_name ?? '-'}`} size="small" color="primary" />
                <Chip label={`status: ${selectedRun?.status ?? '-'}`} size="small" color={statusColor(selectedRun?.status)} />
              </Stack>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(4, minmax(0, 1fr))' } }}>
        <SummaryCard title="Selected run" value={selectedRunId || '-'} />
        <SummaryCard title="Rows" value={String(resultRows.length)} />
        <SummaryCard title="Avg score" value={formatNumber(resultView?.summary.avg_anomaly_score, 3)} />
        <SummaryCard title="Avg health" value={formatNumber(resultView?.summary.avg_health_index, 1)} />
      </Box>

      <Card variant="outlined" sx={{ borderRadius: 1 }}>
        <CardContent>
          <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>
            Score trend / status distribution
          </Typography>
          <Stack spacing={1.2}>
            {statusCounts.map((item) => (
              <Box key={item.status}>
                <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.4 }}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>{item.status}</Typography>
                  <Typography variant="body2" color="text.secondary">{item.count}</Typography>
                </Stack>
                <Box sx={{ height: 8, borderRadius: 1, bgcolor: '#eef2f7', overflow: 'hidden' }}>
                  <Box
                    sx={{
                      height: '100%',
                      width: `${(item.count / maxStatusCount) * 100}%`,
                      bgcolor: item.status === 'CRITICAL' ? '#d32f2f' : item.status === 'WARNING' ? '#ed6c02' : '#2e7d32',
                    }}
                  />
                </Box>
              </Box>
            ))}
          </Stack>
        </CardContent>
      </Card>

      <Card variant="outlined" sx={{ borderRadius: 1 }}>
        <CardContent>
          <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>
            Anomaly result table
          </Typography>

          {loadingResults ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
              <CircularProgress size={28} />
            </Box>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="anomaly result table">
                <TableHead>
                  <TableRow>
                    <TableCell>Window time</TableCell>
                    <TableCell>Equipment</TableCell>
                    <TableCell align="right">Anomaly score</TableCell>
                    <TableCell align="right">Health index</TableCell>
                    <TableCell>Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {resultRows.map((row) => (
                    <TableRow key={`${row.run_id}-${row.equipment_id}-${row.window_start}`} hover>
                      <TableCell>{formatTime(row.window_start)} - {formatTime(row.window_end)}</TableCell>
                      <TableCell>{row.equipment_id ?? '-'}</TableCell>
                      <TableCell align="right">{formatNumber(row.anomaly_score, 3)}</TableCell>
                      <TableCell align="right">{formatNumber(row.health_index, 1)}</TableCell>
                      <TableCell>
                        <Chip size="small" color={statusColor(row.status)} label={row.status ?? '-'} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>
    </Stack>
  );
}

function SummaryCard({ title, value }: { title: string; value: string }) {
  return (
    <Card variant="outlined" sx={{ borderRadius: 1 }}>
      <CardContent>
        <Typography variant="overline" color="text.secondary">
          {title}
        </Typography>
        <Typography variant="h6" sx={{ fontWeight: 800, mt: 0.8 }}>
          {value}
        </Typography>
      </CardContent>
    </Card>
  );
}
