import { Box, Card, CardActionArea, CardContent, Skeleton, Stack, Typography } from '@mui/material';
import type { HomeSummaryCardViewModel } from './types';

type HomeSummaryCardsProps = {
  cards: HomeSummaryCardViewModel[];
  loading: boolean;
  onNavigate: (path: string) => void;
};

function HomeSummaryCardsSkeleton() {
  return (
    <Box
      sx={{
        display: 'grid',
        gap: 0.9,
        gridTemplateColumns: {
          xs: '1fr',
          sm: 'repeat(2, minmax(0, 1fr))',
          lg: 'repeat(5, minmax(0, 1fr))',
        },
      }}
    >
      {Array.from({ length: 5 }).map((_, index) => (
        <Card key={index} variant="outlined">
          <CardContent sx={{ py: 1 }}>
            <Skeleton width={110} height={18} />
            <Skeleton width="75%" height={24} sx={{ mt: 0.35 }} />
            <Skeleton width="100%" height={15} sx={{ mt: 0.4 }} />
          </CardContent>
        </Card>
      ))}
    </Box>
  );
}

export function HomeSummaryCards({ cards, loading, onNavigate }: HomeSummaryCardsProps) {
  if (loading) {
    return <HomeSummaryCardsSkeleton />;
  }

  return (
    <Box
      sx={{
        display: 'grid',
        gap: 0.9,
        gridTemplateColumns: {
          xs: '1fr',
          sm: 'repeat(2, minmax(0, 1fr))',
          lg: 'repeat(5, minmax(0, 1fr))',
        },
      }}
    >
      {cards.map((card) => {
        const CardIcon = card.icon;

        const body = (
          <CardContent
            sx={{
              height: '100%',
              px: 1.25,
              py: 1,
              display: 'flex',
              flexDirection: 'column',
            }}
          >
            <Stack direction="row" alignItems="center" justifyContent="space-between">
              <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                {card.title}
              </Typography>
              <Box
                sx={{
                  width: 26,
                  height: 26,
                  borderRadius: 1.2,
                  display: 'grid',
                  placeItems: 'center',
                  color: card.accentColor,
                  bgcolor: `${card.accentColor}1f`,
                }}
              >
                <CardIcon sx={{ fontSize: 16 }} />
              </Box>
            </Stack>

            <Typography
              variant="subtitle1"
              sx={{
                mt: 0.45,
                fontWeight: 800,
                color: card.accentColor,
                lineHeight: 1.25,
                minHeight: 30,
                display: 'flex',
                alignItems: 'center',
              }}
            >
              {card.value}
            </Typography>

            <Typography
              variant="caption"
              color="text.secondary"
              sx={{
                mt: 0.35,
                lineHeight: 1.35,
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
              }}
            >
              {card.subValue}
            </Typography>
          </CardContent>
        );

        return (
          <Card key={card.id} variant="outlined" sx={{ borderColor: '#d5dfed', height: '100%' }}>
            {card.path ? (
              <CardActionArea onClick={() => onNavigate(card.path as string)} sx={{ height: '100%' }}>
                {body}
              </CardActionArea>
            ) : (
              body
            )}
          </Card>
        );
      })}
    </Box>
  );
}
