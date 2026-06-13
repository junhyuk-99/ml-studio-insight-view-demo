import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded';
import { Box, Button, Card, CardContent, Chip, Skeleton, Stack, Typography } from '@mui/material';
import type { HomePipelineStepViewModel } from './types';

type HomePipelineQuickActionsProps = {
  steps: HomePipelineStepViewModel[];
  loading: boolean;
  onNavigate: (path: string) => void;
};

function PipelineSkeleton() {
  return (
    <Box
      sx={{
        display: 'grid',
        gap: 0.8,
        gridTemplateColumns: {
          xs: '1fr',
          sm: 'repeat(2, minmax(0, 1fr))',
          lg: 'repeat(4, minmax(0, 1fr))',
        },
      }}
    >
      {Array.from({ length: 4 }).map((_, index) => (
        <Card key={index} variant="outlined">
          <CardContent sx={{ py: 1 }}>
            <Skeleton width={95} height={16} />
            <Skeleton width="75%" height={18} sx={{ mt: 0.35 }} />
            <Skeleton width="95%" height={14} sx={{ mt: 0.45 }} />
          </CardContent>
        </Card>
      ))}
    </Box>
  );
}

export function HomePipelineQuickActions({ steps, loading, onNavigate }: HomePipelineQuickActionsProps) {
  if (loading) {
    return <PipelineSkeleton />;
  }

  return (
    <Box
      sx={{
        display: 'flex',
        flexWrap: 'wrap',
        columnGap: 0.6,
        rowGap: 0.75,
        alignItems: 'stretch',
      }}
    >
      {steps.map((step, index) => (
        <Box
          key={step.id}
          sx={{
            display: 'flex',
            alignItems: 'center',
            flex: {
              xs: '1 1 100%',
              sm: '1 1 calc(50% - 6px)',
              lg: '1 1 calc(25% - 8px)',
            },
            maxWidth: {
              xs: '100%',
              sm: 'calc(50% - 6px)',
              lg: 'calc(25% - 8px)',
            },
            minWidth: 0,
            gap: 0.45,
          }}
        >
          <Card
            variant="outlined"
            sx={{
              borderColor: '#d8e2f0',
              width: '100%',
              height: '100%',
              display: 'flex',
            }}
          >
            <CardContent
              sx={{
                px: 1.05,
                py: 0.9,
                display: 'flex',
                flexDirection: 'column',
                width: '100%',
              }}
            >
              <Stack spacing={0.5} sx={{ flexGrow: 1 }}>
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={0.55}>
                  <Chip
                    size="small"
                    color="primary"
                    label={`${step.step}단계`}
                    sx={{
                      fontWeight: 700,
                      height: 24,
                      '& .MuiChip-label': { px: 1 },
                    }}
                  />
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{
                      fontSize: 9.5,
                      textAlign: 'right',
                      lineHeight: 1.25,
                      wordBreak: 'break-word',
                      maxWidth: '54%',
                    }}
                  >
                    {step.fromCollection}
                    <br />
                    → {step.toCollection}
                  </Typography>
                </Stack>

                <Typography
                  variant="subtitle2"
                  sx={{
                    mt: 0.05,
                    fontWeight: 800,
                    lineHeight: 1.25,
                    minHeight: 30,
                    fontSize: 14,
                  }}
                >
                  {step.title}
                </Typography>

                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{
                    minHeight: 32,
                    lineHeight: 1.35,
                    display: '-webkit-box',
                    WebkitLineClamp: 2,
                    WebkitBoxOrient: 'vertical',
                    overflow: 'hidden',
                    wordBreak: 'keep-all',
                    fontSize: 12,
                  }}
                >
                  {step.description}
                </Typography>
              </Stack>

              <Button
                variant={step.enabled ? 'contained' : 'outlined'}
                size="small"
                disabled={!step.enabled}
                onClick={() => onNavigate(step.path)}
                sx={{
                  mt: 0.7,
                  alignSelf: 'flex-start',
                  minWidth: 74,
                  px: 1.1,
                  py: 0.45,
                  fontSize: 12,
                }}
              >
                {step.enabled ? '바로가기' : '권한 없음'}
              </Button>
            </CardContent>
          </Card>

          {index < steps.length - 1 && (
            <Box
              sx={{
                display: { xs: 'none', lg: 'flex' },
                color: '#8da1c1',
                alignSelf: 'center',
                width: 14,
                justifyContent: 'center',
                flex: '0 0 14px',
              }}
            >
              <ArrowForwardRoundedIcon sx={{ fontSize: 17 }} />
            </Box>
          )}
        </Box>
      ))}
    </Box>
  );
}