import { Card, CardContent, Chip, Stack, Typography } from '@mui/material';
import type { AnomalyResultSummary } from '../../types/modelTrain';
import { formatNumber, normalizeStatus, statusChipColor } from './utils';

type AnomalySummaryCardsProps = {
  summary: AnomalyResultSummary;
};

export function AnomalySummaryCards({ summary }: AnomalySummaryCardsProps) {
  const status = normalizeStatus(summary.status);
  const cards = [
    {
      title: 'Current Status',
      value: (
        <Chip
          label={status}
          color={statusChipColor(status)}
          sx={{ mt: 0.6, fontWeight: 700, minWidth: 94 }}
        />
      ),
    },
    {
      title: 'Anomaly Score',
      value: (
        <Typography variant="h6" sx={{ mt: 0.8, fontWeight: 800 }}>
          {formatNumber(summary.latest_anomaly_score ?? summary.avg_anomaly_score)}
        </Typography>
      ),
      helper: `avg ${formatNumber(summary.avg_anomaly_score)}`,
    },
    {
      title: 'Health Index',
      value: (
        <Typography variant="h6" sx={{ mt: 0.8, fontWeight: 800 }}>
          {formatNumber(summary.latest_health_index ?? summary.avg_health_index)}
        </Typography>
      ),
      helper: `avg ${formatNumber(summary.avg_health_index)}`,
    },
    {
      title: 'Anomaly Count',
      value: (
        <Typography variant="h6" sx={{ mt: 0.8, fontWeight: 800 }}>
          {summary.anomaly_count}
          <Typography component="span" variant="body2" color="text.secondary" sx={{ ml: 0.6 }}>
            / {summary.total_count}
          </Typography>
        </Typography>
      ),
    },
  ];

  return (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      spacing={1.2}
      sx={{ '& > *': { flex: 1 } }}
    >
      {cards.map((card) => (
        <Card key={card.title} variant="outlined">
          <CardContent>
            <Typography variant="body2" color="text.secondary">
              {card.title}
            </Typography>
            {card.value}
            {card.helper && (
              <Typography variant="caption" color="text.secondary">
                {card.helper}
              </Typography>
            )}
          </CardContent>
        </Card>
      ))}
    </Stack>
  );
}
