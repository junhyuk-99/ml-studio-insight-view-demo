import { Box, Card, CardContent, Chip, Divider, Skeleton, Stack, Typography } from '@mui/material';
import type {
  HomeCorrelationSummary,
  HomeRecentWindowItem,
  HomeTopSensorItem,
  HomeTrendPoint,
} from './types';

type HomeInsightPanelsProps = {
  loading: boolean;
  trendPoints: HomeTrendPoint[];
  topSensors: HomeTopSensorItem[];
  recentWindows: HomeRecentWindowItem[];
  correlationSummary: HomeCorrelationSummary;
  anomalyPath: string | null;
  correlationPath: string | null;
  onNavigate: (path: string) => void;
};

function statusChipColor(status: string): 'default' | 'success' | 'warning' | 'error' {
  const normalized = status.trim().toUpperCase();

  if (normalized === 'NORMAL') return 'success';
  if (normalized === 'WARNING') return 'warning';
  if (normalized === 'CRITICAL' || normalized === 'FAIL') return 'error';
  return 'default';
}

function InsightSkeletonCard() {
  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <CardContent sx={{ py: 1.1 }}>
        <Skeleton width={160} height={18} />
        <Skeleton width="100%" height={14} sx={{ mt: 0.6 }} />
        <Skeleton width="100%" height={14} />
        <Skeleton width="85%" height={14} />
      </CardContent>
    </Card>
  );
}

function buildMiniCorrelationMatrix(size = 5): number[][] {
  const matrix: number[][] = [];

  for (let row = 0; row < size; row += 1) {
    const rowValues: number[] = [];
    for (let col = 0; col < size; col += 1) {
      if (row === col) {
        rowValues.push(1);
      } else {
        const seed = Math.sin((row + 1) * (col + 2) * 1.73);
        rowValues.push(seed);
      }
    }
    matrix.push(rowValues);
  }

  return matrix;
}

function getCorrelationCellStyle(value: number): string {
  const intensity = Math.min(1, Math.abs(value));
  const alpha = 0.12 + intensity * 0.52;

  if (value >= 0.6) return `rgba(59, 130, 246, ${alpha})`;
  if (value >= 0.2) return `rgba(125, 211, 252, ${alpha})`;
  if (value <= -0.6) return `rgba(239, 68, 68, ${alpha})`;
  if (value <= -0.2) return `rgba(251, 146, 60, ${alpha})`;
  return `rgba(203, 213, 225, ${0.18 + intensity * 0.2})`;
}

const compactCardSx = {
  borderColor: '#d7e2f2',
  height: '100%',
  minHeight: 0,
  display: 'flex',
  flexDirection: 'column',
} as const;

const compactContentSx = {
  px: 1.3,
  py: 1.05,
  display: 'flex',
  flexDirection: 'column',
  minHeight: 0,
  height: '100%',
} as const;

export function HomeInsightPanels({
  loading,
  trendPoints,
  topSensors,
  recentWindows,
  correlationSummary,
  anomalyPath,
  correlationPath,
  onNavigate,
}: HomeInsightPanelsProps) {
  const maxTrendCount = Math.max(1, ...trendPoints.map((point) => point.count));
  const maxSensorCount = Math.max(1, ...topSensors.map((sensor) => sensor.count));
  const totalTrendCount = trendPoints.reduce((sum, point) => sum + point.count, 0);
  const hasTrendData = trendPoints.length > 0;
  const hasNonZeroTrend = trendPoints.some((point) => point.count > 0);
  const miniMatrix = buildMiniCorrelationMatrix(5);

  if (loading) {
    return (
      <Box
        sx={{
          display: 'grid',
          gap: 0.85,
          gridTemplateColumns: { xs: '1fr', lg: '1.12fr 0.66fr 1fr' },
          minHeight: 0,
          height: '100%',
        }}
      >
        <Box
          sx={{
            display: 'grid',
            gap: 0.85,
            gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
            minHeight: 0,
          }}
        >
          <InsightSkeletonCard />
          <InsightSkeletonCard />
        </Box>
        <InsightSkeletonCard />
        <InsightSkeletonCard />
      </Box>
    );
  }

  return (
    <Box
      sx={{
        display: 'grid',
        gap: 0.85,
        gridTemplateColumns: {
          xs: '1fr',
          lg: '1.12fr 0.66fr 1fr',
        },
        minHeight: 0,
        height: '100%',
      }}
    >
      <Box
        sx={{
          display: 'grid',
          gap: 0.85,
          gridTemplateColumns: {
            xs: '1fr',
            md: '1fr 1fr',
          },
          minHeight: 0,
        }}
      >
        <Card variant="outlined" sx={compactCardSx}>
          <CardContent sx={compactContentSx}>
            <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 0.55, fontSize: 16 }}>
              최근 이상 탐지 트렌드
            </Typography>
            <Divider sx={{ mb: 0.55 }} />

            {!hasTrendData ? (
              <Typography variant="body2" color="text.secondary">
                최근 이상 추이 데이터가 없습니다.
              </Typography>
            ) : (
              <Stack spacing={0.55} sx={{ minHeight: 0, height: '100%' }}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                  <Typography variant="caption" color="text.secondary" sx={{ fontSize: 12 }}>
                    최근 7일 이상 탐지 건수
                  </Typography>
                  <Chip
                    size="small"
                    color={totalTrendCount > 0 ? 'error' : 'default'}
                    label={`합계 ${totalTrendCount.toLocaleString('ko-KR')}건`}
                    sx={{ height: 24, '& .MuiChip-label': { px: 1, fontWeight: 700 } }}
                  />
                </Stack>

                {!hasNonZeroTrend && (
                  <Typography variant="caption" color="text.secondary">
                    최근 7일 이상 건수가 모두 0건입니다.
                  </Typography>
                )}

                <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto', pr: 0.1 }}>
                  <Stack spacing={0.42}>
                    {trendPoints.map((point, index) => {
                      const ratio = maxTrendCount > 0 ? (point.count / maxTrendCount) * 100 : 0;

                      const displayLabel =
                        point.date && String(point.date).trim()
                          ? point.date
                          : `D-${trendPoints.length - index - 1}`;

                      return (
                        <Box
                          key={`${displayLabel}-${index}`}
                          sx={{
                            display: 'grid',
                            gridTemplateColumns: '58px minmax(0, 1fr) 28px',
                            gap: 0.6,
                            alignItems: 'center',
                          }}
                        >
                          <Typography
                            variant="caption"
                            color="text.secondary"
                            sx={{
                              fontSize: 12,
                              minWidth: 0,
                              whiteSpace: 'nowrap',
                            }}
                          >
                            {displayLabel}
                          </Typography>

                          <Box sx={{ height: 8, borderRadius: 99, bgcolor: '#edf2fa', overflow: 'hidden' }}>
                            <Box
                              sx={{
                                width: `${Math.max(0, Math.min(100, ratio))}%`,
                                minWidth: point.count > 0 ? 6 : 0,
                                height: '100%',
                                borderRadius: 99,
                                bgcolor: point.count > 0 ? '#ef5350' : '#dbe4f2',
                              }}
                            />
                          </Box>

                          <Typography
                            variant="caption"
                            sx={{ textAlign: 'right', fontWeight: 700, fontSize: 12 }}
                          >
                            {point.count}
                          </Typography>
                        </Box>
                      );
                    })}
                  </Stack>
                </Box>
              </Stack>
            )}
          </CardContent>
        </Card>

        <Card variant="outlined" sx={compactCardSx}>
          <CardContent sx={compactContentSx}>
            <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 0.55, fontSize: 16 }}>
              이상치 많은 데이터 Top 5
            </Typography>
            <Divider sx={{ mb: 0.55 }} />

            {topSensors.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                최근 이상 결과에서 데이터 Top 정보를 계산할 수 없습니다.
              </Typography>
            ) : (
              <Stack spacing={0.55} sx={{ minHeight: 0, flex: 1 }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontSize: 12 }}>
                  최근 이상 결과 기준 상위 원인 데이터/피처
                </Typography>

                <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto', pr: 0.1 }}>
                  <Stack spacing={0.55}>
                    {topSensors.map((sensor, index) => {
                      const ratio = maxSensorCount > 0 ? (sensor.count / maxSensorCount) * 100 : 0;

                      return (
                        <Box
                          key={sensor.sensor}
                          sx={{
                            border: '1px solid #e8eef7',
                            borderRadius: 1.15,
                            px: 0.85,
                            py: 0.65,
                          }}
                        >
                          <Stack direction="row" spacing={0.75} alignItems="center">
                            <Box
                              sx={{
                                width: 22,
                                minWidth: 22,
                                height: 22,
                                borderRadius: '50%',
                                display: 'grid',
                                placeItems: 'center',
                                bgcolor: '#f2f6fc',
                                color: '#4a5d7d',
                              }}
                            >
                              <Typography variant="caption" sx={{ fontWeight: 800 }}>
                                {index + 1}
                              </Typography>
                            </Box>

                            <Box sx={{ flex: 1, minWidth: 0 }}>
                              <Stack direction="row" justifyContent="space-between" spacing={1}>
                                <Typography
                                  variant="caption"
                                  sx={{
                                    fontWeight: 700,
                                    minWidth: 0,
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                    fontSize: 12,
                                  }}
                                  title={sensor.sensor}
                                >
                                  {sensor.sensor}
                                </Typography>

                                <Typography
                                  variant="caption"
                                  sx={{ fontWeight: 800, color: '#ff7a45', fontSize: 12 }}
                                >
                                  {sensor.count}
                                </Typography>
                              </Stack>

                              <Box
                                sx={{
                                  mt: 0.45,
                                  height: 7,
                                  borderRadius: 99,
                                  bgcolor: '#edf2fa',
                                  overflow: 'hidden',
                                }}
                              >
                                <Box
                                  sx={{
                                    width: `${Math.max(0, Math.min(100, ratio))}%`,
                                    minWidth: sensor.count > 0 ? 6 : 0,
                                    height: '100%',
                                    borderRadius: 99,
                                    bgcolor: '#ff7a45',
                                  }}
                                />
                              </Box>
                            </Box>
                          </Stack>
                        </Box>
                      );
                    })}
                  </Stack>
                </Box>
              </Stack>
            )}
          </CardContent>
        </Card>
      </Box>

      <Card variant="outlined" sx={compactCardSx}>
        <CardContent sx={{ ...compactContentSx, py: 0.9 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 0.45, fontSize: 15 }}>
            상관관계 요약
          </Typography>
          <Divider sx={{ mb: 0.55 }} />

          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: '20px repeat(5, minmax(0, 1fr))',
              gridTemplateRows: '16px repeat(5, minmax(0, 1fr))',
              gap: 0.22,
              mb: 0.65,
              p: 0.45,
              borderRadius: 1,
              bgcolor: '#f8fbff',
              border: '1px solid #e3ebf6',
              width: '100%',
              maxWidth: 400,
              alignSelf: 'center',
            }}
          >
            <Box />
            {['A', 'B', 'C', 'D', 'E'].map((label) => (
              <Typography
                key={`x-${label}`}
                variant="caption"
                sx={{
                  fontSize: 9,
                  textAlign: 'center',
                  color: 'text.secondary',
                  lineHeight: 1.2,
                }}
              >
                {label}
              </Typography>
            ))}

            {miniMatrix.map((row, rowIndex) => (
              <Box
                key={`row-${rowIndex}`}
                sx={{
                  display: 'contents',
                }}
              >
                <Typography
                  variant="caption"
                  sx={{
                    fontSize: 9,
                    textAlign: 'center',
                    color: 'text.secondary',
                    lineHeight: '22px',
                  }}
                >
                  {['A', 'B', 'C', 'D', 'E'][rowIndex]}
                </Typography>

                {row.map((value, colIndex) => (
                  <Box
                    key={`${rowIndex}-${colIndex}`}
                    sx={{
                      height: 30,
                      borderRadius: 0.45,
                      bgcolor: getCorrelationCellStyle(value),
                      border:
                        rowIndex === colIndex
                          ? '1px solid rgba(59,130,246,0.28)'
                          : '1px solid transparent',
                    }}
                  />
                ))}
              </Box>
            ))}
          </Box>

          <Typography variant="body2" sx={{ fontWeight: 800, fontSize: 13.5 }}>
            {correlationSummary.title}
          </Typography>

          <Typography
            variant="caption"
            color="text.secondary"
            sx={{
              mt: 0.3,
              fontSize: 11.5,
              lineHeight: 1.4,
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
            }}
          >
            {correlationSummary.description}
          </Typography>

          <Typography
            variant="caption"
            color="text.secondary"
            sx={{
              mt: 0.25,
              fontSize: 11.5,
              lineHeight: 1.4,
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
            }}
          >
            {correlationSummary.detail}
          </Typography>

          <Stack
            direction="row"
            spacing={0.5}
            sx={{
              mt: 'auto',
              pt: 0.7,
              flexWrap: 'wrap',
              rowGap: 0.5,
            }}
          >
            <Chip
              size="small"
              label={`상관쌍 ${correlationSummary.pairCount.toLocaleString('ko-KR')}개`}
              sx={{
                height: 22,
                '& .MuiChip-label': { px: 0.9, fontWeight: 700, fontSize: 11 },
              }}
            />
            {correlationPath && (
              <Chip
                size="small"
                color="primary"
                label="상세 이동"
                onClick={() => onNavigate(correlationPath)}
                clickable
                sx={{
                  height: 22,
                  '& .MuiChip-label': { px: 0.9, fontWeight: 700, fontSize: 11 },
                }}
              />
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card variant="outlined" sx={compactCardSx}>
        <CardContent sx={compactContentSx}>
          <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 0.55, fontSize: 16 }}>
            최근 이상 탐지 발생 구간
          </Typography>
          <Divider sx={{ mb: 0.6 }} />

          {recentWindows.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              최근 이상 발생 구간 데이터가 없습니다.
            </Typography>
          ) : (
            <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto', pr: 0.1, maxHeight: { lg: 250 } }}>
              <Stack spacing={0.6}>
                {recentWindows.map((windowItem) => (
                  <Box
                    key={windowItem.id}
                    sx={{
                      border: '1px solid #e1e8f3',
                      borderRadius: 1.1,
                      px: 0.8,
                      py: 0.55,
                    }}
                  >
                    <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={0.8}>
                      <Typography variant="caption" sx={{ fontWeight: 700, fontSize: 12 }}>
                        {`${windowItem.windowStart ?? '-'} ~ ${windowItem.windowEnd ?? '-'}`}
                      </Typography>
                      <Chip
                        size="small"
                        color={statusChipColor(windowItem.status)}
                        label={windowItem.status}
                        sx={{ height: 24, '& .MuiChip-label': { px: 1, fontWeight: 700 } }}
                      />
                    </Stack>

                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ mt: 0.35, display: 'block', fontSize: 12 }}
                    >
                      점수 {windowItem.anomalyScore == null ? '-' : String(windowItem.anomalyScore)}
                    </Typography>

                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{
                        mt: 0.15,
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        fontSize: 12,
                        lineHeight: 1.45,
                      }}
                    >
                      주요 원인: {windowItem.causes.length > 0 ? windowItem.causes.join(', ') : '데이터 없음'}
                    </Typography>

                    {anomalyPath && (
                      <Typography
                        variant="caption"
                        sx={{
                          mt: 0.35,
                          display: 'inline-block',
                          color: 'primary.main',
                          cursor: 'pointer',
                          fontWeight: 700,
                          fontSize: 12,
                        }}
                        onClick={() => onNavigate(anomalyPath)}
                      >
                        상세 결과 보기
                      </Typography>
                    )}
                  </Box>
                ))}
              </Stack>
            </Box>
          )}
        </CardContent>
      </Card>
    </Box>
  );
}
