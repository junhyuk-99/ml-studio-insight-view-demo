import { Alert, Box, Card, CardContent, Divider, Stack, Typography } from '@mui/material';
import type { AnomalyResultPoint } from '../../types/modelTrain';

type IsolationForestSectionProps = {
  rows: AnomalyResultPoint[];
  titlePrefix?: string | null;
};

type HistogramBin = {
  label: string;
  count: number;
};

function buildHistogramBins(rows: AnomalyResultPoint[], binCount = 10): HistogramBin[] {
  const scores = rows
    .map((row) => row.anomaly_score)
    .filter((value): value is number => typeof value === 'number' && Number.isFinite(value));
  if (scores.length === 0) {
    return [];
  }

  const min = Math.min(...scores, 0);
  const max = Math.max(...scores, 1);
  const range = max - min > 0 ? max - min : 1;
  const step = range / binCount;
  const buckets = Array.from({ length: binCount }, () => 0);

  scores.forEach((score) => {
    const rawIndex = Math.floor((score - min) / step);
    const index = Math.min(binCount - 1, Math.max(0, rawIndex));
    buckets[index] += 1;
  });

  return buckets.map((count, index) => {
    const start = min + step * index;
    const end = start + step;
    return {
      label: `${start.toFixed(2)} - ${end.toFixed(2)}`,
      count,
    };
  });
}

export function IsolationForestSection({ rows, titlePrefix }: IsolationForestSectionProps) {
  const bins = buildHistogramBins(rows);
  const maxCount = bins.reduce((accumulator, bin) => Math.max(accumulator, bin.count), 1);

  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="subtitle1" fontWeight={700}>
          {titlePrefix ? `${titlePrefix} ` : ''}Isolation Forest Analysis
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.4 }}>
          Tree-based isolation score distribution for identifying concentrated anomaly ranges.
        </Typography>
        <Divider sx={{ my: 1.2 }} />

        {bins.length === 0 && <Alert severity="info">No score data available for histogram analysis.</Alert>}

        {bins.length > 0 && (
          <Stack spacing={0.8}>
            {bins.map((bin) => {
              const percent = maxCount === 0 ? 0 : (bin.count / maxCount) * 100;
              return (
                <Box key={bin.label} sx={{ display: 'grid', gridTemplateColumns: '140px 1fr 36px', gap: 1, alignItems: 'center' }}>
                  <Typography variant="caption" color="text.secondary">
                    {bin.label}
                  </Typography>
                  <Box sx={{ height: 12, backgroundColor: '#edf2fb', borderRadius: 999 }}>
                    <Box
                      sx={{
                        width: `${percent}%`,
                        height: '100%',
                        borderRadius: 999,
                        backgroundColor: '#1660cf',
                      }}
                    />
                  </Box>
                  <Typography variant="caption" sx={{ textAlign: 'right', fontWeight: 700 }}>
                    {bin.count}
                  </Typography>
                </Box>
              );
            })}
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}

