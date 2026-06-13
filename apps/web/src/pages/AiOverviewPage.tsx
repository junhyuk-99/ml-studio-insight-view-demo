import RefreshIcon from '@mui/icons-material/Refresh';
import Grid from '@mui/material/Grid';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Skeleton,
  Stack,
  Tab,
  Tabs,
  Typography,
} from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { modelTrainService } from '../services/modelTrainService';
import type {
  AiOverviewData,
  AiOverviewDatasetModel,
  AiOverviewSupervisedSummary,
} from '../types/modelTrain';

const STATUS_ORDER = ['NORMAL', 'WARNING', 'CRITICAL', 'NO_DATA'] as const;
const CONFUSION_ORDER = ['TP', 'TN', 'FP', 'FN'] as const;
const SUMMARY_TYPE_SUPERVISED = 'SUPERVISED';
const SUMMARY_TYPE_UNSUPERVISED = 'UNSUPERVISED';

function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '데이터 없음';
  }
  const parsedDate = new Date(value);
  if (Number.isNaN(parsedDate.getTime())) {
    return value;
  }
  return parsedDate.toLocaleString('ko-KR', { hour12: false });
}

function formatPercent(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return '데이터 없음';
  }
  return `${(value * 100).toFixed(1)}%`;
}

function formatDecimal(value: number | null | undefined, digits = 4): string {
  if (value == null || Number.isNaN(value)) {
    return '데이터 없음';
  }
  return value.toFixed(digits);
}

function formatCount(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return '데이터 없음';
  }
  return value.toLocaleString('ko-KR');
}

function formatCountWithUnit(value: number | null | undefined, unit: string): string {
  if (value == null || Number.isNaN(value)) {
    return '데이터 없음';
  }
  return `${value.toLocaleString('ko-KR')}${unit}`;
}

function statusLabel(status: string | null | undefined): string {
  if (!status) {
    return 'NO_DATA';
  }
  return status.toUpperCase();
}

function statusChipColor(
  status: string | null | undefined,
): 'default' | 'success' | 'warning' | 'error' | 'info' {
  if (!status) {
    return 'default';
  }
  switch (status.toUpperCase()) {
    case 'SUCCESS':
    case 'NORMAL':
      return 'success';
    case 'FAIL':
    case 'CRITICAL':
      return 'error';
    case 'RUNNING':
      return 'info';
    case 'WARNING':
    case 'SKIPPED':
      return 'warning';
    case 'NO_DATA':
      return 'default';
    default:
      return 'default';
  }
}

function statusBarColor(status: string): string {
  switch (status.toUpperCase()) {
    case 'NORMAL':
    case 'TP':
    case 'TN':
      return '#1f8f4d';
    case 'WARNING':
      return '#d88305';
    case 'CRITICAL':
    case 'FP':
    case 'FN':
      return '#cb2b2b';
    case 'NO_DATA':
      return '#6a7b94';
    default:
      return '#4f6da1';
  }
}

function dominantSummaryStatus(statusCounts: Record<string, number>, totalCount: number): string {
  if ((statusCounts.CRITICAL ?? 0) > 0) {
    return 'CRITICAL';
  }
  if ((statusCounts.WARNING ?? 0) > 0) {
    return 'WARNING';
  }
  if ((statusCounts.NORMAL ?? 0) > 0) {
    return 'NORMAL';
  }
  if (totalCount <= 0) {
    return 'NO_DATA';
  }
  return 'NO_DATA';
}

function safeUpper(value: string | null | undefined): string {
  return value ? value.toUpperCase() : '';
}

function modelTabId(model: AiOverviewDatasetModel): string {
  const datasetKey = model.dataset_key ?? 'NO_DATASET';
  const policyId = model.active_policy_id ?? model.active_algo_code ?? 'NO_POLICY';
  return `${datasetKey}::${policyId}`;
}

function resolveDatasetSource(model: AiOverviewDatasetModel): string {
  if (model.source_collection && model.source_collection.trim().length > 0) {
    return model.source_collection.trim();
  }
  if (
    model.feature_summary?.source_dataset_name &&
    model.feature_summary.source_dataset_name.trim().length > 0
  ) {
    return model.feature_summary.source_dataset_name.trim();
  }
  if (model.dataset_key && model.dataset_key.includes('_')) {
    return model.dataset_key.split('_')[0];
  }
  return model.dataset_key ?? 'unknown';
}

function resolveSummaryType(model: AiOverviewDatasetModel): string {
  const summaryType = safeUpper(model.summary_type);
  if (summaryType === SUMMARY_TYPE_SUPERVISED || summaryType === SUMMARY_TYPE_UNSUPERVISED) {
    return summaryType;
  }
  if (safeUpper(model.active_algo_code) === 'RANDOM_FOREST') {
    return SUMMARY_TYPE_SUPERVISED;
  }
  return SUMMARY_TYPE_UNSUPERVISED;
}

function isSupervisedModel(model: AiOverviewDatasetModel): boolean {
  return resolveSummaryType(model) === SUMMARY_TYPE_SUPERVISED;
}

function buildTabLabel(model: AiOverviewDatasetModel): string {
  const algoName = model.active_algo_name ?? model.active_algo_code ?? 'Unknown';
  const source = resolveDatasetSource(model);
  return `${algoName} - ${source}`;
}

function supervisedCount(summary: AiOverviewSupervisedSummary | undefined, key: (typeof CONFUSION_ORDER)[number]): number {
  if (!summary) {
    return 0;
  }
  switch (key) {
    case 'TP':
      return summary.tp ?? 0;
    case 'TN':
      return summary.tn ?? 0;
    case 'FP':
      return summary.fp ?? 0;
    case 'FN':
      return summary.fn ?? 0;
    default:
      return 0;
  }
}

type KeyValueRowProps = {
  label: string;
  value: string;
};

function KeyValueRow({ label, value }: KeyValueRowProps) {
  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', sm: '180px minmax(0, 1fr)' },
        alignItems: 'center',
        gap: 1,
        py: 0.75,
      }}
    >
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography
        variant="body2"
        sx={{
          fontWeight: 700,
          minWidth: 0,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
        title={value}
      >
        {value}
      </Typography>
    </Box>
  );
}

type KpiCardProps = {
  title: string;
  value: string;
  subLabel: string;
  chipStatus?: string | null;
};

function KpiCard({ title, value, subLabel, chipStatus }: KpiCardProps) {
  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <CardContent sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
        <Typography variant="body2" color="text.secondary">
          {title}
        </Typography>

        <Box sx={{ mt: 1.25, minHeight: 44, display: 'flex', alignItems: 'center' }}>
          {chipStatus ? (
            <Chip
              label={statusLabel(chipStatus)}
              color={statusChipColor(chipStatus)}
              size="medium"
              sx={{ fontWeight: 800, fontSize: 15 }}
            />
          ) : (
            <Typography
              variant="h5"
              sx={{ fontWeight: 800, lineHeight: 1.1, wordBreak: 'break-word' }}
              title={value}
            >
              {value}
            </Typography>
          )}
        </Box>

        <Typography
          variant="body2"
          color="text.secondary"
          sx={{
            mt: 'auto',
            pt: 1.2,
            minHeight: 38,
            display: 'flex',
            alignItems: 'flex-end',
          }}
          title={subLabel}
        >
          {subLabel}
        </Typography>
      </CardContent>
    </Card>
  );
}

function OverviewSkeleton() {
  return (
    <Stack spacing={2}>
      <Grid container spacing={2}>
        {Array.from({ length: 4 }).map((_, index) => (
          <Grid key={index} size={{ xs: 12, sm: 6, lg: 3 }}>
            <Card variant="outlined">
              <CardContent>
                <Skeleton width={110} height={20} />
                <Skeleton width="85%" height={44} sx={{ mt: 1 }} />
                <Skeleton width="100%" height={22} sx={{ mt: 1 }} />
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card variant="outlined">
            <CardContent>
              <Skeleton width={220} height={28} />
              <Skeleton width="100%" height={26} />
              <Skeleton width="100%" height={26} />
              <Skeleton width="100%" height={26} />
              <Skeleton width="100%" height={26} />
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card variant="outlined">
            <CardContent>
              <Skeleton width={200} height={28} />
              <Skeleton width="100%" height={26} />
              <Skeleton width="100%" height={26} />
              <Skeleton width="100%" height={26} />
              <Skeleton width="100%" height={26} />
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}

export function AiOverviewPage() {
  const [overview, setOverview] = useState<AiOverviewData | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [localFetchedAt, setLocalFetchedAt] = useState<string | null>(null);
  const [selectedTabId, setSelectedTabId] = useState<string | null>(null);

  const loadOverview = useCallback(async (isRefresh: boolean) => {
    if (isRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    setErrorMessage(null);

    try {
      const data = await modelTrainService.getOverview();
      setOverview(data);
      setLocalFetchedAt(new Date().toISOString());
    } catch (error: unknown) {
      setErrorMessage(error instanceof Error ? error.message : 'AI 운영 상태 조회에 실패했습니다.');
    } finally {
      if (isRefresh) {
        setRefreshing(false);
      } else {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    void loadOverview(false);
  }, [loadOverview]);

  const activeModels = useMemo(() => {
    const models = overview?.active_models ?? [];
    return models.map((model) => ({
      id: modelTabId(model),
      model,
    }));
  }, [overview?.active_models]);

  useEffect(() => {
    if (activeModels.length === 0) {
      setSelectedTabId(null);
      return;
    }
    const exists = selectedTabId && activeModels.some((item) => item.id === selectedTabId);
    if (!exists) {
      setSelectedTabId(activeModels[0].id);
    }
  }, [activeModels, selectedTabId]);

  const selectedModel = useMemo(() => {
    if (activeModels.length === 0) {
      return null;
    }
    const matched = selectedTabId ? activeModels.find((item) => item.id === selectedTabId) : null;
    return (matched ?? activeModels[0]).model;
  }, [activeModels, selectedTabId]);

  const isSupervised = selectedModel ? isSupervisedModel(selectedModel) : false;
  const latestRun = selectedModel?.latest_run;
  const summary = selectedModel?.summary;
  const supervisedSummary = selectedModel?.supervised_summary;
  const featureSummary = selectedModel?.feature_summary;
  const labeledDataSummary = selectedModel?.labeled_data_summary;

  const mergedStatusCounts = useMemo(() => {
    const merged: Record<string, number> = {
      NORMAL: 0,
      WARNING: 0,
      CRITICAL: 0,
      NO_DATA: 0,
    };

    if (isSupervised) {
      return merged;
    }

    const rawCounts = summary?.status_counts ?? {};
    for (const [rawStatus, rawCount] of Object.entries(rawCounts)) {
      const normalizedStatus = rawStatus.trim().toUpperCase();
      if (!normalizedStatus) {
        continue;
      }
      const count = Number.isFinite(rawCount) ? Number(rawCount) : 0;
      merged[normalizedStatus] = (merged[normalizedStatus] ?? 0) + count;
    }
    return merged;
  }, [isSupervised, summary?.status_counts]);

  const statusDistributionRows = useMemo(() => {
    const knownRows = STATUS_ORDER.map((status) => ({
      status,
      count: mergedStatusCounts[status] ?? 0,
    }));

    const extraRows = Object.entries(mergedStatusCounts)
      .filter(([status]) => !STATUS_ORDER.includes(status as (typeof STATUS_ORDER)[number]))
      .map(([status, count]) => ({ status, count }));

    return [...knownRows, ...extraRows];
  }, [mergedStatusCounts]);

  const totalStatusCount = statusDistributionRows.reduce((sum, row) => sum + row.count, 0);
  const dominantStatus = dominantSummaryStatus(mergedStatusCounts, summary?.total_count ?? 0);

  const confusionRows = useMemo(() => {
    if (!isSupervised || !supervisedSummary) {
      return [];
    }
    return CONFUSION_ORDER.map((key) => ({
      key,
      count: supervisedCount(supervisedSummary, key),
    }));
  }, [isSupervised, supervisedSummary]);

  const totalConfusionCount = confusionRows.reduce((sum, row) => sum + row.count, 0);

  const overviewSummary = useMemo(() => {
    const total = activeModels.length;
    let successCount = 0;
    let failCount = 0;
    let noDataCount = 0;

    for (const item of activeModels) {
      const runStatus = safeUpper(item.model.latest_run?.status);
      if (runStatus === 'SUCCESS') {
        successCount += 1;
      }
      if (runStatus === 'FAIL') {
        failCount += 1;
      }

      const hasData = isSupervisedModel(item.model)
        ? (item.model.supervised_summary?.classification_result_count ?? 0) > 0 ||
          (item.model.supervised_summary?.test_count ?? 0) > 0
        : (item.model.summary?.total_count ?? 0) > 0;

      if (runStatus === 'NO_DATA' || !hasData) {
        noDataCount += 1;
      }
    }

    return {
      total,
      successCount,
      failCount,
      noDataCount,
    };
  }, [activeModels]);

  if (loading && !overview) {
    return (
      <Stack spacing={2}>
        <Box>
          <Typography variant="h4">AI 운영 상태</Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mt: 0.5 }}>
            활성 모델과 최근 실행, 결과 요약을 dataset 단위로 확인할 수 있습니다.
          </Typography>
        </Box>
        <OverviewSkeleton />
      </Stack>
    );
  }

  return (
    <Stack spacing={2.2}>
      <Card variant="outlined" sx={{ borderColor: '#cdd9ea' }}>
        <CardContent>
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            alignItems={{ xs: 'flex-start', md: 'center' }}
            justifyContent="space-between"
            spacing={1.5}
          >
            <Box>
              <Typography variant="h4">AI 운영 상태</Typography>
              <Typography variant="body1" color="text.secondary" sx={{ mt: 0.5 }}>
                활성 모델, 최근 실행, 요약 지표를 알고리즘 유형별로 분리해 보여줍니다.
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ mt: 0.9, display: 'block' }}>
                마지막 갱신: {formatDateTime(overview?.refreshed_at ?? localFetchedAt)}
              </Typography>
            </Box>

            <Button
              variant="outlined"
              startIcon={<RefreshIcon />}
              disabled={refreshing}
              onClick={() => void loadOverview(true)}
              sx={{ minWidth: 130, fontWeight: 700 }}
            >
              {refreshing ? '불러오는 중...' : '새로고침'}
            </Button>
          </Stack>
        </CardContent>
      </Card>

      {errorMessage && <Alert severity="error">{errorMessage}</Alert>}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard
            title="활성 모델 수"
            value={formatCount(overviewSummary.total)}
            subLabel={`총 ${formatCount(overview?.total_active_model_count ?? overviewSummary.total)}개`}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard
            title="정상 실행 모델 수"
            value={formatCount(overviewSummary.successCount)}
            subLabel="latest run = SUCCESS"
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard
            title="오류 모델 수"
            value={formatCount(overviewSummary.failCount)}
            subLabel="latest run = FAIL"
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <KpiCard
            title="데이터 없음 모델 수"
            value={formatCount(overviewSummary.noDataCount)}
            subLabel="run/summary 데이터 없음"
          />
        </Grid>
      </Grid>

      {activeModels.length === 0 ? (
        <Alert severity="info">현재 활성화된 모델이 없습니다.</Alert>
      ) : (
        <Card variant="outlined" sx={{ borderColor: '#d6e0ef' }}>
          <CardContent sx={{ pb: 1.1 }}>
            <Tabs
              value={selectedTabId ?? activeModels[0].id}
              onChange={(_, nextValue: string) => setSelectedTabId(nextValue)}
              variant="scrollable"
              scrollButtons="auto"
              allowScrollButtonsMobile
              aria-label="active model tabs"
            >
              {activeModels.map((item) => (
                <Tab
                  key={item.id}
                  value={item.id}
                  sx={{ textTransform: 'none', alignItems: 'flex-start', py: 1.1 }}
                  label={
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                        {buildTabLabel(item.model)}
                      </Typography>
                      <Chip
                        size="small"
                        label={statusLabel(item.model.latest_run?.status)}
                        color={statusChipColor(item.model.latest_run?.status)}
                        sx={{ fontWeight: 700 }}
                      />
                    </Stack>
                  }
                />
              ))}
            </Tabs>
          </CardContent>
        </Card>
      )}

      {selectedModel && (
        <>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard
                title="현재 알고리즘"
                value={selectedModel.active_algo_name || selectedModel.active_algo_code || '데이터 없음'}
                subLabel={`active policy id: ${selectedModel.active_policy_id || '데이터 없음'}`}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard
                title="최근 실행 상태"
                value={statusLabel(latestRun?.status)}
                chipStatus={latestRun?.status}
                subLabel={`${formatDateTime(latestRun?.executed_at)} | ${latestRun?.trigger_type || 'TRIGGER 없음'}`}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard
                title={isSupervised ? 'Accuracy' : '평균 건강 지수'}
                value={isSupervised ? formatPercent(supervisedSummary?.accuracy) : formatPercent(summary?.avg_health_index)}
                subLabel={isSupervised ? 'thismodeleval 최신 평가 기준' : 'thisanomalyresult 최신 요약 기준'}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <KpiCard
                title={isSupervised ? '오분류 건수' : 'Anomaly 건수'}
                value={
                  isSupervised
                    ? formatCountWithUnit(supervisedSummary?.misclassified_count, '건')
                    : formatCountWithUnit(summary?.anomaly_count, '건')
                }
                subLabel={
                  isSupervised
                    ? `전체 test ${formatCount(supervisedSummary?.test_count)}건 기준`
                    : `전체 ${formatCount(summary?.total_count)}건 중`
                }
              />
            </Grid>
          </Grid>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <Card variant="outlined" sx={{ height: '100%' }}>
                <CardContent>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    선택 활성 모델 상태
                  </Typography>
                  <Divider sx={{ my: 1.2 }} />

                  <KeyValueRow
                    label="현재 알고리즘"
                    value={selectedModel.active_algo_name || selectedModel.active_algo_code || '데이터 없음'}
                  />
                  <KeyValueRow label="active policy id" value={selectedModel.active_policy_id || '데이터 없음'} />
                  <KeyValueRow label="dataset key" value={selectedModel.dataset_key || '데이터 없음'} />
                  <KeyValueRow label="dataset label" value={selectedModel.dataset_label || '데이터 없음'} />
                  <KeyValueRow label="source collection" value={resolveDatasetSource(selectedModel)} />
                  <KeyValueRow label="summary type" value={resolveSummaryType(selectedModel)} />
                  <KeyValueRow
                    label="현재 Feature 설정 window_size"
                    value={selectedModel.window_size == null ? '데이터 없음' : String(selectedModel.window_size)}
                  />
                  <KeyValueRow label="selected columns" value={`${formatCount(selectedModel.selected_column_count)}개`} />
                  <KeyValueRow label="등록일" value={formatDateTime(selectedModel.reg_date)} />
                  <KeyValueRow label="마지막 변경" value={formatDateTime(selectedModel.updated_at)} />
                </CardContent>
              </Card>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <Card variant="outlined" sx={{ height: '100%' }}>
                <CardContent>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    최근 실행 상세
                  </Typography>
                  <Divider sx={{ my: 1.2 }} />

                  <KeyValueRow label="run id" value={latestRun?.run_id || '데이터 없음'} />
                  <KeyValueRow label="실행 상태" value={statusLabel(latestRun?.status)} />
                  <KeyValueRow label="trigger type" value={latestRun?.trigger_type || '데이터 없음'} />
                  <KeyValueRow label="started at" value={formatDateTime(latestRun?.started_at)} />
                  <KeyValueRow label="ended at" value={formatDateTime(latestRun?.ended_at)} />
                  <KeyValueRow label="실행 시각" value={formatDateTime(latestRun?.executed_at)} />
                  <KeyValueRow
                    label="알고리즘명"
                    value={latestRun?.algo_name || latestRun?.algo_code || '데이터 없음'}
                  />
                  <KeyValueRow label="dataset key" value={latestRun?.dataset_key || '데이터 없음'} />
                  <KeyValueRow label="메시지" value={latestRun?.message || '데이터 없음'} />
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          {!isSupervised ? (
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 8 }}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Stack
                      direction={{ xs: 'column', sm: 'row' }}
                      alignItems={{ xs: 'flex-start', sm: 'center' }}
                      justifyContent="space-between"
                      spacing={1}
                    >
                      <Typography variant="h6" sx={{ fontWeight: 800 }}>
                        이상 탐지 결과 요약
                      </Typography>
                      <Chip
                        size="small"
                        label={`현재 상태: ${statusLabel(dominantStatus)}`}
                        color={statusChipColor(dominantStatus)}
                        sx={{ fontWeight: 700 }}
                      />
                    </Stack>

                    <Grid container spacing={1.4} sx={{ mt: 1 }}>
                      <Grid size={{ xs: 12, sm: 4 }}>
                        <Box sx={{ p: 1.3, border: '1px solid #e1e8f2', borderRadius: 2 }}>
                          <Typography variant="body2" color="text.secondary">
                            평균 anomaly score
                          </Typography>
                          <Typography variant="h6" sx={{ mt: 0.6, fontWeight: 800 }}>
                            {formatDecimal(summary?.avg_anomaly_score, 4)}
                          </Typography>
                        </Box>
                      </Grid>
                      <Grid size={{ xs: 12, sm: 4 }}>
                        <Box sx={{ p: 1.3, border: '1px solid #e1e8f2', borderRadius: 2 }}>
                          <Typography variant="body2" color="text.secondary">
                            평균 health index
                          </Typography>
                          <Typography variant="h6" sx={{ mt: 0.6, fontWeight: 800 }}>
                            {formatPercent(summary?.avg_health_index)}
                          </Typography>
                        </Box>
                      </Grid>
                      <Grid size={{ xs: 12, sm: 4 }}>
                        <Box sx={{ p: 1.3, border: '1px solid #e1e8f2', borderRadius: 2 }}>
                          <Typography variant="body2" color="text.secondary">
                            anomaly count
                          </Typography>
                          <Typography variant="h6" sx={{ mt: 0.6, fontWeight: 800 }}>
                            {formatCountWithUnit(summary?.anomaly_count, '건')}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            전체 {formatCount(summary?.total_count)}건 중
                          </Typography>
                        </Box>
                      </Grid>
                    </Grid>
                  </CardContent>
                </Card>
              </Grid>

              <Grid size={{ xs: 12, md: 4 }}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>
                      Status 분포
                    </Typography>

                    <Stack spacing={1.1}>
                      {statusDistributionRows.map((row) => {
                        const ratio = totalStatusCount > 0 ? (row.count / totalStatusCount) * 100 : 0;
                        return (
                          <Box key={row.status}>
                            <Stack direction="row" alignItems="center" justifyContent="space-between">
                              <Typography variant="body2" color="text.secondary">
                                {statusLabel(row.status)}
                              </Typography>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                {formatCount(row.count)}
                              </Typography>
                            </Stack>
                            <Box
                              sx={{
                                mt: 0.5,
                                height: 7,
                                borderRadius: 99,
                                bgcolor: '#edf2f8',
                                overflow: 'hidden',
                              }}
                            >
                              <Box
                                sx={{
                                  width: `${Math.max(0, Math.min(100, ratio))}%`,
                                  height: '100%',
                                  borderRadius: 99,
                                  bgcolor: statusBarColor(row.status),
                                }}
                              />
                            </Box>
                          </Box>
                        );
                      })}
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>
          ) : (
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 8 }}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>
                      지도학습 분류 결과 요약
                    </Typography>
                    <Divider sx={{ my: 1.2 }} />

                    <Grid container spacing={1.4}>
                      <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                        <KeyValueRow label="Accuracy" value={formatPercent(supervisedSummary?.accuracy)} />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                        <KeyValueRow label="Precision" value={formatPercent(supervisedSummary?.precision)} />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                        <KeyValueRow label="Recall" value={formatPercent(supervisedSummary?.recall)} />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                        <KeyValueRow label="F1 Score" value={formatPercent(supervisedSummary?.f1_score)} />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                        <KeyValueRow label="Test Count" value={formatCountWithUnit(supervisedSummary?.test_count, '건')} />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                        <KeyValueRow
                          label="정답 건수"
                          value={formatCountWithUnit(supervisedSummary?.correct_count, '건')}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                        <KeyValueRow
                          label="오분류 건수"
                          value={formatCountWithUnit(supervisedSummary?.misclassified_count, '건')}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6, md: 6 }}>
                        <KeyValueRow
                          label="classification result 건수"
                          value={formatCountWithUnit(supervisedSummary?.classification_result_count, '건')}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6, md: 6 }}>
                        <KeyValueRow
                          label="최신 평가 실행일"
                          value={formatDateTime(supervisedSummary?.latest_eval_executed_at)}
                        />
                      </Grid>
                    </Grid>
                  </CardContent>
                </Card>
              </Grid>

              <Grid size={{ xs: 12, md: 4 }}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Typography variant="h6" sx={{ fontWeight: 800, mb: 1 }}>
                      Confusion Matrix
                    </Typography>

                    <Stack spacing={1.1}>
                      {confusionRows.map((row) => {
                        const ratio = totalConfusionCount > 0 ? (row.count / totalConfusionCount) * 100 : 0;
                        return (
                          <Box key={row.key}>
                            <Stack direction="row" alignItems="center" justifyContent="space-between">
                              <Typography variant="body2" color="text.secondary">
                                {row.key}
                              </Typography>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                {formatCount(row.count)}
                              </Typography>
                            </Stack>
                            <Box
                              sx={{
                                mt: 0.5,
                                height: 7,
                                borderRadius: 99,
                                bgcolor: '#edf2f8',
                                overflow: 'hidden',
                              }}
                            >
                              <Box
                                sx={{
                                  width: `${Math.max(0, Math.min(100, ratio))}%`,
                                  height: '100%',
                                  borderRadius: 99,
                                  bgcolor: statusBarColor(row.key),
                                }}
                              />
                            </Box>
                          </Box>
                        );
                      })}
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>
          )}

          {!isSupervised ? (
            <Card variant="outlined">
              <CardContent>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  Feature 데이터 상태
                </Typography>
                <Divider sx={{ my: 1.2 }} />

                <Grid container spacing={1.4}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <KeyValueRow
                      label="feature 총 건수"
                      value={formatCountWithUnit(featureSummary?.total_feature_count, '건')}
                    />
                    <KeyValueRow
                      label="최신 feature 생성 시각"
                      value={formatDateTime(featureSummary?.latest_feature_created_at)}
                    />
                    <KeyValueRow
                      label="현재 Feature 설정 window_size"
                      value={featureSummary?.window_size == null ? '데이터 없음' : String(featureSummary.window_size)}
                    />
                  </Grid>

                  <Grid size={{ xs: 12, md: 6 }}>
                    <KeyValueRow
                      label="selected columns 개수"
                      value={`${formatCount(featureSummary?.selected_column_count)}개`}
                    />
                    <KeyValueRow
                      label="source dataset 이름"
                      value={featureSummary?.source_dataset_name || resolveDatasetSource(selectedModel)}
                    />
                    <KeyValueRow label="dataset label" value={featureSummary?.dataset_label || '데이터 없음'} />
                  </Grid>
                </Grid>
              </CardContent>
            </Card>
          ) : (
            <Card variant="outlined">
              <CardContent>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                  라벨링 데이터 상태
                </Typography>
                <Divider sx={{ my: 1.2 }} />

                <Grid container spacing={1.4}>
                  <Grid size={{ xs: 12, md: 6 }}>
                    <KeyValueRow
                      label="source collection"
                      value={labeledDataSummary?.source_collection || '라벨 데이터 미준비'}
                    />
                    <KeyValueRow label="dataset key" value={labeledDataSummary?.dataset_key || '데이터 없음'} />
                    <KeyValueRow label="label version" value={labeledDataSummary?.label_version || '데이터 없음'} />
                    <KeyValueRow
                      label="total_count"
                      value={formatCountWithUnit(labeledDataSummary?.total_count, '건')}
                    />
                    <KeyValueRow
                      label="train_count"
                      value={formatCountWithUnit(labeledDataSummary?.train_count, '건')}
                    />
                  </Grid>

                  <Grid size={{ xs: 12, md: 6 }}>
                    <KeyValueRow
                      label="test_count"
                      value={formatCountWithUnit(labeledDataSummary?.test_count, '건')}
                    />
                    <KeyValueRow
                      label="excluded_unknown_count"
                      value={formatCountWithUnit(labeledDataSummary?.excluded_unknown_count, '건')}
                    />
                    <KeyValueRow
                      label="normal_count"
                      value={formatCountWithUnit(labeledDataSummary?.normal_count, '건')}
                    />
                    <KeyValueRow
                      label="anomaly_count"
                      value={formatCountWithUnit(labeledDataSummary?.anomaly_count, '건')}
                    />
                    <KeyValueRow
                      label="feature importance top"
                      value={
                        supervisedSummary?.feature_importance_top?.length
                          ? supervisedSummary.feature_importance_top
                              .map((item) => `${item.rank}. ${item.feature} (${formatDecimal(item.importance, 4)})`)
                              .join(', ')
                          : '데이터 없음'
                      }
                    />
                  </Grid>
                </Grid>
              </CardContent>
            </Card>
          )}
        </>
      )}

      <Card variant="outlined" sx={{ backgroundColor: '#f8fbff', borderColor: '#d9e4f2' }}>
        <CardContent>
          <Typography variant="body2" color="text.secondary">
            본 화면은 `tmst_model_active(use_flag='Y')`의 활성 모델을 dataset 기준으로 보여줍니다.
          </Typography>
          {isSupervised ? (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.6 }}>
              지도학습(Random Forest) 상세 분석은 `/ai/supervised-result`에서 확인할 수 있습니다.
            </Typography>
          ) : (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.6 }}>
              비지도 상세 분석은 `/ai/anomaly`, `/ai/health-index`, `/ai/threshold-alert`에서 확인할 수 있습니다.
            </Typography>
          )}
        </CardContent>
      </Card>
    </Stack>
  );
}
