import type { ElementType } from 'react';

export type HomeSummaryCardViewModel = {
  id: string;
  title: string;
  description: string;
  value: string;
  subValue: string;
  path: string | null;
  accentColor: string;
  icon: ElementType;
};

export type HomeTrendPoint = {
  date: string;
  label?: string;
  count: number;
};

export type HomeTopSensorItem = {
  sensor: string;
  count: number;
};

export type HomeRecentWindowItem = {
  id: string;
  windowStart: string | null;
  windowEnd: string | null;
  timeRange?: string;
  status: string;
  anomalyScore: number | null;
  score?: number | null;
  causes: string[];
};

export type HomeCorrelationSummary = {
  title: string;
  description: string;
  detail: string;
  pairCount: number;
};

export type HomePipelineStepViewModel = {
  id: string;
  step: number;
  title: string;
  description: string;
  path: string;
  fromCollection: string;
  toCollection: string;
  enabled: boolean;
};
