import { Box, Divider, Drawer, Stack, Typography } from '@mui/material';
import type { AnomalyResultPoint, AnomalyRunDetail } from '../../types/modelTrain';
import { formatDateTime, formatNumber, normalizeStatus } from './utils';

type AnomalyDetailDrawerProps = {
  open: boolean;
  run: AnomalyRunDetail | null;
  row: AnomalyResultPoint | null;
  onClose: () => void;
};

function JsonBlock({ value }: { value: unknown }) {
  return (
    <Box
      component="pre"
      sx={{
        m: 0,
        p: 1,
        borderRadius: 1.5,
        border: '1px solid #d6deea',
        backgroundColor: '#f8fbff',
        overflowX: 'auto',
        fontSize: 12,
      }}
    >
      {JSON.stringify(value, null, 2)}
    </Box>
  );
}

export function AnomalyDetailDrawer({ open, run, row, onClose }: AnomalyDetailDrawerProps) {
  return (
    <Drawer anchor="right" open={open} onClose={onClose}>
      <Box sx={{ width: { xs: 320, sm: 440 }, p: 2 }}>
        <Typography variant="h6">Anomaly Detail</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.3 }}>
          Selected result row and run metadata.
        </Typography>
        <Divider sx={{ my: 1.2 }} />

        {!row && <Typography variant="body2">Select a row in the result table.</Typography>}

        {row && (
          <Stack spacing={1.2}>
            <Box>
              <Typography variant="subtitle2" fontWeight={700}>
                Result Point
              </Typography>
              <Typography variant="body2" color="text.secondary">
                window_start: {formatDateTime(row.window_start)}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                window_end: {formatDateTime(row.window_end)}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                anomaly_score: {formatNumber(row.anomaly_score)}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                health_index: {formatNumber(row.health_index)}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                status: {normalizeStatus(row.status)} / is_anomaly: {row.is_anomaly ? 'Y' : 'N'}
              </Typography>
            </Box>

            <Box>
              <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 0.6 }}>
                input_features
              </Typography>
              <JsonBlock value={row.input_features} />
            </Box>
          </Stack>
        )}

        {run && (
          <Stack spacing={1.2} sx={{ mt: 1.6 }}>
            <Divider />
            <Typography variant="subtitle2" fontWeight={700}>
              Run Metadata
            </Typography>
            <Typography variant="body2" color="text.secondary">
              run_id: {run.run_id}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              algo: {run.algo_code} ({run.algo_name})
            </Typography>
            <Typography variant="body2" color="text.secondary">
              dataset_key: {run.dataset_key ?? '-'}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              equipment_id: {run.equipment_id ?? '-'}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              reg_date: {formatDateTime(run.reg_date)}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              updated_at: {formatDateTime(run.updated_at)}
            </Typography>

            <Box>
              <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 0.6 }}>
                params
              </Typography>
              <JsonBlock value={run.params} />
            </Box>
          </Stack>
        )}
      </Box>
    </Drawer>
  );
}
