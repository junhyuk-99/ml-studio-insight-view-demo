import {
  Alert,
  Card,
  CardContent,
  Chip,
  Divider,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import type { AnomalyResultPoint } from '../../types/modelTrain';
import { anomalyRowKey, formatDateTime, formatNumber, normalizeStatus, statusChipColor } from './utils';

type AnomalyResultsTableProps = {
  rows: AnomalyResultPoint[];
  selectedRowKey: string | null;
  onSelectRow: (row: AnomalyResultPoint, rowKey: string) => void;
};

export function AnomalyResultsTable({ rows, selectedRowKey, onSelectRow }: AnomalyResultsTableProps) {
  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="subtitle1" fontWeight={700}>
          탐지 결과
        </Typography>
        <Divider sx={{ my: 1.2 }} />

        {rows.length === 0 && <Alert severity="info">조회된 이상 탐지 결과가 없습니다.</Alert>}

        {rows.length > 0 && (
          <TableContainer sx={{ border: '1px solid #d6deea', borderRadius: 1, maxHeight: 460 }}>
            <Table stickyHeader size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>구간 시작</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>구간 종료</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>이상 점수</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>건강 지수</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>상태</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>이상 여부</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row, index) => {
                  const rowKey = anomalyRowKey(row, index);
                  const isSelected = selectedRowKey === rowKey;
                  const status = normalizeStatus(row.status);
                  return (
                    <TableRow
                      key={rowKey}
                      hover
                      selected={isSelected}
                      onClick={() => onSelectRow(row, rowKey)}
                      sx={{ cursor: 'pointer' }}
                    >
                      <TableCell>{formatDateTime(row.window_start)}</TableCell>
                      <TableCell>{formatDateTime(row.window_end)}</TableCell>
                      <TableCell>{formatNumber(row.anomaly_score)}</TableCell>
                      <TableCell>{formatNumber(row.health_index)}</TableCell>
                      <TableCell>
                        <Chip size="small" label={status} color={statusChipColor(status)} />
                      </TableCell>
                      <TableCell>{row.is_anomaly ? 'Y' : 'N'}</TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </CardContent>
    </Card>
  );
}
