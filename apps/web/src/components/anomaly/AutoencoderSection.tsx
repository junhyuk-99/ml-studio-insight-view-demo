import { Alert, Box, Card, CardContent, Divider, Stack, Typography } from '@mui/material';
import type { AnomalyResultPoint } from '../../types/modelTrain';
import { formatDateTime, formatNumber } from './utils';

type AutoencoderSectionProps = {
  rows: AnomalyResultPoint[];
  selectedRow: AnomalyResultPoint | null;
  titlePrefix?: string | null;
};

type FeatureBarItem = {
  key: string;
  value: number;
  ratio: number;
};

function toNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string') {
    const parsed = Number(value.trim());
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function collectTopFeatures(row: AnomalyResultPoint | null, topN = 10): FeatureBarItem[] {
  if (!row || !row.input_features) {
    return [];
  }

  const numericEntries = Object.entries(row.input_features)
    .map(([key, value]) => ({ key, value: toNumber(value) }))
    .filter((entry): entry is { key: string; value: number } => entry.value !== null)
    .sort((left, right) => Math.abs(right.value) - Math.abs(left.value))
    .slice(0, topN);

  if (numericEntries.length === 0) {
    return [];
  }

  const maxAbs = numericEntries.reduce((accumulator, entry) => Math.max(accumulator, Math.abs(entry.value)), 0) || 1;
  return numericEntries.map((entry) => ({
    key: entry.key,
    value: entry.value,
    ratio: (Math.abs(entry.value) / maxAbs) * 100,
  }));
}

export function AutoencoderSection({ rows, selectedRow, titlePrefix }: AutoencoderSectionProps) {
  const targetRow = selectedRow ?? (rows.length > 0 ? rows[rows.length - 1] : null);
  const topFeatures = collectTopFeatures(targetRow);

  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="subtitle1" fontWeight={700}>
          {titlePrefix ? `${titlePrefix} ` : ''}AutoEncoder Analysis
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.4 }}>
          Reconstruction-error perspective analysis. If reconstruction error is absent, anomaly score is used.
        </Typography>
        <Divider sx={{ my: 1.2 }} />

        {!targetRow && <Alert severity="info">No row is selected for feature analysis.</Alert>}

        {targetRow && (
          <Stack spacing={1.2}>
            <Box sx={{ p: 1, border: '1px solid #d6deea', borderRadius: 1, backgroundColor: '#f8fbff' }}>
              <Typography variant="caption" color="text.secondary">
                Target window
              </Typography>
              <Typography variant="body2" sx={{ mt: 0.3 }}>
                {formatDateTime(targetRow.window_start)} - {formatDateTime(targetRow.window_end)}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.3 }}>
                Anomaly score: {formatNumber(targetRow.anomaly_score)} / Health index: {formatNumber(targetRow.health_index)}
              </Typography>
            </Box>

            {topFeatures.length === 0 && (
              <Alert severity="info">No numeric input features available for the feature chart.</Alert>
            )}

            {topFeatures.length > 0 && (
              <Stack spacing={0.8}>
                {topFeatures.map((feature) => (
                  <Box key={feature.key} sx={{ display: 'grid', gridTemplateColumns: '1fr 140px', gap: 1, alignItems: 'center' }}>
                    <Box>
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.3 }}>
                        {feature.key}
                      </Typography>
                      <Box sx={{ height: 12, backgroundColor: '#edf2fb', borderRadius: 999 }}>
                        <Box
                          sx={{
                            width: `${feature.ratio}%`,
                            height: '100%',
                            borderRadius: 999,
                            backgroundColor: '#2b8f6d',
                          }}
                        />
                      </Box>
                    </Box>
                    <Typography variant="caption" sx={{ textAlign: 'right', fontWeight: 700 }}>
                      {formatNumber(feature.value)}
                    </Typography>
                  </Box>
                ))}
              </Stack>
            )}
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}

