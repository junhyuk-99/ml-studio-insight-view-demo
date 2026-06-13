import type { ReactElement } from 'react';
import { AiOverviewPage } from '../pages/AiOverviewPage';
import { AnomalyCausePage } from '../pages/AnomalyCausePage';
import { AlgorithmSelectionPage } from '../pages/AlgorithmSelectionPage';
import { AnomalyDetectionPage } from '../pages/AnomalyDetectionPage';
import { DataExplorationBoxplotPage } from '../pages/DataExplorationBoxplotPage';
import { DataExplorationCorrelationHeatmapPage } from '../pages/DataExplorationCorrelationHeatmapPage';
import { DataExplorationHistogramPage } from '../pages/DataExplorationHistogramPage';
import { DataExplorationProcessFlowPage } from '../pages/DataExplorationProcessFlowPage';
import { DataExplorationTimeseriesPage } from '../pages/DataExplorationTimeseriesPage';
import { HealthIndexPage } from '../pages/HealthIndexPage';
import { HomePage } from '../pages/HomePage';
import { ModelTrainPage } from '../pages/ModelTrainPage';
import { PreprocessPage } from '../pages/PreprocessPage';
import { SupervisedResultPage } from '../pages/SupervisedResultPage';
import { TestPreprocessPage } from '../pages/TestPreprocessPage';
import { ThresholdAlertPage } from '../pages/ThresholdAlertPage';
import { UserManagementPage } from '../pages/UserManagementPage';
import type { UserRole } from '../types/auth';
import type { AppRouteId } from './navigationConfig';
import { getRoutePathById } from './navigationConfig';

export type ProtectedRouteDefinition = {
  id: AppRouteId;
  path: string;
  element: ReactElement;
  index?: boolean;
  allowedRoles?: UserRole[];
};

export const PROTECTED_ROUTE_DEFINITIONS: readonly ProtectedRouteDefinition[] = [
  {
    id: 'home',
    path: getRoutePathById('home'),
    element: <HomePage />,
    index: true,
  },
  {
    id: 'operation-algorithm',
    path: getRoutePathById('operation-algorithm'),
    element: <AlgorithmSelectionPage />,
  },
  {
    id: 'operation-preprocess',
    path: getRoutePathById('operation-preprocess'),
    element: <PreprocessPage />,
  },
  {
    id: 'operation-testpreprocess',
    path: getRoutePathById('operation-testpreprocess'),
    element: <TestPreprocessPage />,
  },
  {
    id: 'operation-modeltrain',
    path: getRoutePathById('operation-modeltrain'),
    element: <ModelTrainPage />,
  },
  {
    id: 'operation-user',
    path: getRoutePathById('operation-user'),
    element: <UserManagementPage />,
    allowedRoles: ['admin'],
  },
  {
    id: 'ai-overview',
    path: getRoutePathById('ai-overview'),
    element: <AiOverviewPage />,
  },
  {
    id: 'ai-anomaly',
    path: getRoutePathById('ai-anomaly'),
    element: <AnomalyDetectionPage />,
  },
  {
    id: 'ai-health-index',
    path: getRoutePathById('ai-health-index'),
    element: <HealthIndexPage />,
  },
  {
    id: 'ai-anomaly-cause',
    path: getRoutePathById('ai-anomaly-cause'),
    element: <AnomalyCausePage />,
  },
  {
    id: 'ai-threshold-alert',
    path: getRoutePathById('ai-threshold-alert'),
    element: <ThresholdAlertPage />,
  },
  {
    id: 'ai-supervised-result',
    path: getRoutePathById('ai-supervised-result'),
    element: <SupervisedResultPage />,
  },
  {
    id: 'data-exploration-histogram',
    path: getRoutePathById('data-exploration-histogram'),
    element: <DataExplorationHistogramPage />,
  },
  {
    id: 'data-exploration-timeseries',
    path: getRoutePathById('data-exploration-timeseries'),
    element: <DataExplorationTimeseriesPage />,
  },
  {
    id: 'data-exploration-correlation-heatmap',
    path: getRoutePathById('data-exploration-correlation-heatmap'),
    element: <DataExplorationCorrelationHeatmapPage />,
  },
  {
    id: 'data-exploration-boxplot',
    path: getRoutePathById('data-exploration-boxplot'),
    element: <DataExplorationBoxplotPage />,
  },
  {
    id: 'data-exploration-processflow',
    path: getRoutePathById('data-exploration-processflow'),
    element: <DataExplorationProcessFlowPage />,
  },
] as const;
