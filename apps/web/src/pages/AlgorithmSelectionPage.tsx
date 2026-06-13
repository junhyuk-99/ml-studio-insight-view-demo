import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import PsychologyIcon from '@mui/icons-material/Psychology';
import Grid from '@mui/material/Grid';
import {
  Alert,
  Box,
  Button,
  Card,
  CardActionArea,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import {
  defaultAlgorithmDatasetKey,
  isRandomForestAlgorithm,
  randomForestDatasetKey,
} from '../constants/algorithmDatasetPolicy';
import { getAlgorithmMeta } from '../constants/algorithmMeta';
import { getAlgorithmVisualMeta } from '../constants/algorithmVisualMeta';
import { algorithmSelectionService } from '../services/algorithmSelectionService';
import { dataExplorationService } from '../services/dataExplorationService';
import { useAuth } from '../store/AuthContext';
import { useSelectedAlgorithm } from '../store/SelectedAlgorithmContext';
import type {
  AlgoOption,
  AlgorithmActiveSelectionData,
  AlgorithmComparisonMeta,
  AlgorithmSelectionData,
} from '../types/algorithmSelection';
import type { OperationDatasetOption } from '../utils/operationDataset';
import {
  extractDatasetKeyFromRouteState,
  filterOperationDatasetOptions,
  persistOperationDatasetKey,
  readPersistedOperationDatasetKey,
  resolveOperationDatasetKeyWithFallback,
  resolveOperationDatasetLabel,
} from '../utils/operationDataset';

const METRIC_ITEMS: Array<{ label: string; key: keyof Omit<AlgorithmComparisonMeta, 'summary'> }> = [
  { label: '정확도', key: 'accuracy' },
  { label: '학습 속도', key: 'trainSpeed' },
  { label: '예측 속도', key: 'predictSpeed' },
  { label: '복잡도', key: 'complexity' },
  { label: '해석 가능성', key: 'interpretability' },
];

const BADGE_STYLE_BY_VALUE: Record<string, { backgroundColor: string; color: string; borderColor: string }> = {
  '매우 높음': { backgroundColor: '#e9f7ef', color: '#0f6b3f', borderColor: '#6fca93' },
  높음: { backgroundColor: '#ecf5ff', color: '#0f4fa0', borderColor: '#82b8f7' },
  보통: { backgroundColor: '#f2f4f7', color: '#44536a', borderColor: '#c7d0de' },
  낮음: { backgroundColor: '#fff5e8', color: '#8f4a07', borderColor: '#efb16b' },
  '매우 낮음': { backgroundColor: '#ffecec', color: '#9d1f1f', borderColor: '#e48a8a' },
  '매우 빠름': { backgroundColor: '#e8faf1', color: '#0e6f40', borderColor: '#76d5a4' },
  빠름: { backgroundColor: '#eaf4ff', color: '#0f4fa0', borderColor: '#7db6f7' },
  느림: { backgroundColor: '#fff5e8', color: '#8f4a07', borderColor: '#efb16b' },
  '매우 느림': { backgroundColor: '#ffecec', color: '#9d1f1f', borderColor: '#e48a8a' },
  중간: { backgroundColor: '#f2f4f7', color: '#44536a', borderColor: '#c7d0de' },
};

const DEFAULT_BADGE_STYLE = BADGE_STYLE_BY_VALUE['보통'];

function getBadgeStyle(value: string) {
  return BADGE_STYLE_BY_VALUE[value] ?? DEFAULT_BADGE_STYLE;
}
type AlgoSelectionPointer = {
  typeCd: string;
  algoCd: string;
};

function normalizeAlgoCode(algoCode: string | null | undefined): string | null {
  if (typeof algoCode !== 'string') {
    return null;
  }
  const trimmed = algoCode.trim();
  if (trimmed.length === 0) {
    return null;
  }
  return trimmed.toUpperCase();
}

function normalizeDatasetKey(datasetKey: string | null | undefined): string | null {
  if (typeof datasetKey !== 'string') {
    return null;
  }
  const trimmed = datasetKey.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function findFirstSelectableAlgorithm(selectionData: AlgorithmSelectionData | null): AlgoSelectionPointer | null {
  if (!selectionData) {
    return null;
  }

  for (const algoType of selectionData.algoTypes) {
    const options = selectionData.algorithmsByType[algoType.algoTypeCd] ?? [];
    const firstOption = options[0];
    if (firstOption?.algoCd) {
      return {
        typeCd: algoType.algoTypeCd,
        algoCd: firstOption.algoCd,
      };
    }
  }

  return null;
}

function findAlgorithmPointerByCode(
  selectionData: AlgorithmSelectionData | null,
  algoCode: string | null | undefined,
): AlgoSelectionPointer | null {
  if (!selectionData) {
    return null;
  }

  const normalizedAlgoCode = normalizeAlgoCode(algoCode);
  if (!normalizedAlgoCode) {
    return null;
  }

  for (const algoType of selectionData.algoTypes) {
    const options = selectionData.algorithmsByType[algoType.algoTypeCd] ?? [];
    const matchedOption = options.find((option) => normalizeAlgoCode(option.algoCd) === normalizedAlgoCode);
    if (matchedOption) {
      return {
        typeCd: algoType.algoTypeCd,
        algoCd: matchedOption.algoCd,
      };
    }
  }

  return null;
}

function buildActiveMappingFallbackMessage(activeSelection: AlgorithmActiveSelectionData): string {
  const policyId = activeSelection.active_policy_id ?? '-';
  const algoCode = activeSelection.active_algo_code ?? '-';
  return `활성 정책(${policyId})의 알고리즘(${algoCode})이 현재 추천 목록에 없어 카드 선택 상태를 매핑하지 못했습니다.`;
}

function toApplyFailureMessage(error: unknown, algoCode: string): string {
  if (!(error instanceof Error)) {
    return '선택한 알고리즘 적용에 실패했습니다.';
  }

  const message = error.message?.trim();
  if (!message) {
    return '선택한 알고리즘 적용에 실패했습니다.';
  }

  if (
    message.includes('No active tmst_model_policy')
    || message.includes('Unsupported algo_code')
    || message.includes('아직 정책이 등록되지 않은 알고리즘')
  ) {
    return `아직 정책이 등록되지 않은 알고리즘입니다. (algo_code=${algoCode})`;
  }

  return message;
}

export function AlgorithmSelectionPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const { user } = useAuth();
  const { setSelectedAlgorithm, clearSelectedAlgorithm } = useSelectedAlgorithm();

  const [datasetOptions, setDatasetOptions] = useState<OperationDatasetOption[]>([]);
  const [loadingDatasetOptions, setLoadingDatasetOptions] = useState(true);
  const [datasetSelectionWarning, setDatasetSelectionWarning] = useState<string | null>(null);
  const [selectedDatasetKey, setSelectedDatasetKey] = useState('');

  const [selectionData, setSelectionData] = useState<AlgorithmSelectionData | null>(null);
  const [activeSelectionsByDataset, setActiveSelectionsByDataset] = useState<
    Record<string, AlgorithmActiveSelectionData | null>
  >({});
  const [activeSelectionLookupMessage, setActiveSelectionLookupMessage] = useState<string | null>(null);
  const [selectedTypeCd, setSelectedTypeCd] = useState('');
  const [selectedAlgoCd, setSelectedAlgoCd] = useState('');
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [applyErrorMessage, setApplyErrorMessage] = useState<string | null>(null);
  const [applyingSelection, setApplyingSelection] = useState(false);
  const stateDatasetKey = useMemo(() => extractDatasetKeyFromRouteState(location.state), [location.state]);
  const queryDatasetKey = useMemo(() => searchParams.get('datasetKey')?.trim() ?? '', [searchParams]);

  useEffect(() => {
    let isActive = true;
    setLoadingDatasetOptions(true);

    dataExplorationService
      .getDatasets()
      .then((rawOptions) => {
        if (!isActive) {
          return;
        }

        const operationOptions = filterOperationDatasetOptions(rawOptions ?? []);
        setDatasetOptions(operationOptions);

        const resolved = resolveOperationDatasetKeyWithFallback({
          datasetOptions: operationOptions,
          queryDatasetKey,
          stateDatasetKey,
          persistedDatasetKey: readPersistedOperationDatasetKey(),
          defaultDatasetKey: defaultAlgorithmDatasetKey(),
        });

        setSelectedDatasetKey(resolved.datasetKey);
        setDatasetSelectionWarning(resolved.warning);
      })
      .catch((error: unknown) => {
        if (!isActive) {
          return;
        }
        setDatasetOptions([]);
        setSelectedDatasetKey(defaultAlgorithmDatasetKey());
        setDatasetSelectionWarning(`dataset 목록을 불러오지 못해 기본 dataset(${defaultAlgorithmDatasetKey()})을 사용합니다.`);
        setErrorMessage(error instanceof Error ? error.message : 'dataset 목록을 불러오지 못했습니다.');
      })
      .finally(() => {
        if (!isActive) {
          return;
        }
        setLoadingDatasetOptions(false);
      });

    return () => {
      isActive = false;
    };
  }, [queryDatasetKey, stateDatasetKey]);

  useEffect(() => {
    const normalizedDatasetKey = normalizeDatasetKey(selectedDatasetKey);
    if (!normalizedDatasetKey) {
      return;
    }

    persistOperationDatasetKey(normalizedDatasetKey);

    if (queryDatasetKey === normalizedDatasetKey) {
      return;
    }

    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('datasetKey', normalizedDatasetKey);
    setSearchParams(nextParams, { replace: true });
  }, [queryDatasetKey, searchParams, selectedDatasetKey, setSearchParams]);

  const cacheActiveSelection = useCallback((datasetKey: string, activeSelection: AlgorithmActiveSelectionData | null) => {
    const normalizedDatasetKey = normalizeDatasetKey(datasetKey);
    if (!normalizedDatasetKey) {
      return;
    }

    setActiveSelectionsByDataset((previous) => {
      const next = {
        ...previous,
        [normalizedDatasetKey]: activeSelection,
      };

      const activeDatasetKey = normalizeDatasetKey(activeSelection?.dataset_key);
      if (activeDatasetKey) {
        next[activeDatasetKey] = activeSelection;
      }

      return next;
    });
  }, []);

  const applySelectionDataToView = useCallback(
    (data: AlgorithmSelectionData, datasetKey: string) => {
      setSelectionData(data);
      cacheActiveSelection(datasetKey, data.activeSelection);

      const nextActiveSelection = data.activeSelection;

      const mappedByActiveAlgo = findAlgorithmPointerByCode(data, nextActiveSelection?.active_algo_code);
      const fallbackSelection = findFirstSelectableAlgorithm(data);
      const effectiveSelection = mappedByActiveAlgo ?? fallbackSelection;

      if (effectiveSelection) {
        setSelectedTypeCd(effectiveSelection.typeCd);
        setSelectedAlgoCd(effectiveSelection.algoCd);
      } else {
        setSelectedTypeCd('');
        setSelectedAlgoCd('');
      }

      const activeAlgoCd = nextActiveSelection?.active_algo_code?.trim();
      if (!activeAlgoCd) {
        clearSelectedAlgorithm();
        return;
      }

      const activeAlgoNm = nextActiveSelection?.active_algo_name?.trim() || activeAlgoCd;
      setSelectedAlgorithm({
        algoCd: activeAlgoCd,
        algoNm: activeAlgoNm,
      });
    },
    [cacheActiveSelection, clearSelectedAlgorithm, setSelectedAlgorithm],
  );

  useEffect(() => {
    if (loadingDatasetOptions) {
      return;
    }

    const normalizedDatasetKey = normalizeDatasetKey(selectedDatasetKey);
    if (!normalizedDatasetKey) {
      setLoading(false);
      setSelectionData(null);
      setSelectedTypeCd('');
      setSelectedAlgoCd('');
      clearSelectedAlgorithm();
      setErrorMessage('유효한 dataset을 선택하세요.');
      return;
    }

    let isActive = true;

    setLoading(true);
    setErrorMessage(null);

    algorithmSelectionService
      .getSelectionOptions(normalizedDatasetKey)
      .then((data) => {
        if (!isActive) {
          return;
        }
        applySelectionDataToView(data, normalizedDatasetKey);
      })
      .catch((error: unknown) => {
        if (!isActive) {
          return;
        }
        setSelectionData(null);
        setActiveSelectionsByDataset({});
        setActiveSelectionLookupMessage(null);
        setSelectedTypeCd('');
        setSelectedAlgoCd('');
        clearSelectedAlgorithm();
        setErrorMessage(error instanceof Error ? error.message : '알고리즘 목록을 불러오지 못했습니다.');
      })
      .finally(() => {
        if (!isActive) {
          return;
        }
        setLoading(false);
      });

    return () => {
      isActive = false;
    };
  }, [applySelectionDataToView, clearSelectedAlgorithm, loadingDatasetOptions, selectedDatasetKey]);

  useEffect(() => {
    if (!selectionData || !selectedTypeCd) {
      return;
    }

    const algorithms = selectionData.algorithmsByType[selectedTypeCd] ?? [];
    if (algorithms.length === 0) {
      if (selectedAlgoCd !== '') {
        setSelectedAlgoCd('');
      }
      return;
    }

    const hasSelected = algorithms.some((algorithm) => algorithm.algoCd === selectedAlgoCd);
    if (!hasSelected) {
      setSelectedAlgoCd(algorithms[0].algoCd);
    }
  }, [selectionData, selectedTypeCd, selectedAlgoCd]);

  const algoTypes = selectionData?.algoTypes ?? [];
  const currentAlgorithms = useMemo(() => {
    if (!selectionData || !selectedTypeCd) {
      return [] as AlgoOption[];
    }
    return selectionData.algorithmsByType[selectedTypeCd] ?? [];
  }, [selectionData, selectedTypeCd]);

  const selectedType = useMemo(() => {
    return algoTypes.find((type) => type.algoTypeCd === selectedTypeCd) ?? null;
  }, [algoTypes, selectedTypeCd]);

  const selectedAlgorithm = useMemo(() => {
    return currentAlgorithms.find((algorithm) => algorithm.algoCd === selectedAlgoCd) ?? null;
  }, [currentAlgorithms, selectedAlgoCd]);

  const resolvedDatasetKey = useMemo(() => {
    return normalizeDatasetKey(selectedDatasetKey) ?? defaultAlgorithmDatasetKey();
  }, [selectedDatasetKey]);

  const hasResolvedDatasetSelection = useMemo(() => {
    return Object.prototype.hasOwnProperty.call(activeSelectionsByDataset, resolvedDatasetKey);
  }, [activeSelectionsByDataset, resolvedDatasetKey]);

  const activeSelection = useMemo(() => {
    return activeSelectionsByDataset[resolvedDatasetKey] ?? null;
  }, [activeSelectionsByDataset, resolvedDatasetKey]);

  const selectedDatasetOption = useMemo(() => {
    const normalizedResolvedDatasetKey = normalizeDatasetKey(resolvedDatasetKey);
    if (!normalizedResolvedDatasetKey) {
      return null;
    }
    return datasetOptions.find((option) => normalizeDatasetKey(option.datasetKey) === normalizedResolvedDatasetKey) ?? null;
  }, [datasetOptions, resolvedDatasetKey]);

  useEffect(() => {
    if (hasResolvedDatasetSelection) {
      setActiveSelectionLookupMessage(null);
    }
  }, [hasResolvedDatasetSelection, resolvedDatasetKey]);

  useEffect(() => {
    if (!selectionData || hasResolvedDatasetSelection) {
      return;
    }

    let isActive = true;
    setActiveSelectionLookupMessage(null);

    algorithmSelectionService
      .getSelectionOptions(resolvedDatasetKey)
      .then((data) => {
        if (!isActive) {
          return;
        }
        cacheActiveSelection(resolvedDatasetKey, data.activeSelection);
      })
      .catch((error: unknown) => {
        if (!isActive) {
          return;
        }
        cacheActiveSelection(resolvedDatasetKey, null);
        setActiveSelectionLookupMessage(
          error instanceof Error
            ? `dataset(${resolvedDatasetKey}) 활성 정책 조회 실패: ${error.message}`
            : `dataset(${resolvedDatasetKey}) 활성 정책 조회에 실패했습니다.`,
        );
      });

    return () => {
      isActive = false;
    };
  }, [cacheActiveSelection, hasResolvedDatasetSelection, resolvedDatasetKey, selectionData]);

  const activeSelectionMappingMessage = useMemo(() => {
    if (!activeSelection) {
      return null;
    }
    const mappedByActiveAlgo = findAlgorithmPointerByCode(selectionData, activeSelection.active_algo_code);
    if (mappedByActiveAlgo) {
      return null;
    }
    return buildActiveMappingFallbackMessage(activeSelection);
  }, [activeSelection, selectionData]);

  const randomForestGuideMessage = useMemo(() => {
    if (!isRandomForestAlgorithm(selectedAlgorithm?.algoCd)) {
      return null;
    }
    return `Random Forest 라벨 데이터셋(${randomForestDatasetKey()})은 현재 운영에서 미활성 상태입니다. 라벨 데이터 준비 전 자동 실행은 비활성입니다.`;
  }, [selectedAlgorithm?.algoCd]);

  const selectedMeta = useMemo(() => {
    if (!selectedAlgorithm) {
      return null;
    }
    return getAlgorithmMeta(selectedAlgorithm.algoCd);
  }, [selectedAlgorithm]);

  const selectedVisualMeta = useMemo(() => {
    return getAlgorithmVisualMeta(selectedAlgorithm?.algoCd);
  }, [selectedAlgorithm]);

  const isCurrentSelectionApplied = useMemo(() => {
    const currentAlgoCode = normalizeAlgoCode(selectedAlgorithm?.algoCd);
    const activeAlgoCode = normalizeAlgoCode(activeSelection?.active_algo_code);
    return currentAlgoCode !== null && currentAlgoCode === activeAlgoCode;
  }, [activeSelection?.active_algo_code, selectedAlgorithm?.algoCd]);

  const handleApplyAndMoveToModelTrain = async () => {
    if (!selectedAlgorithm) {
      return;
    }

    const normalizedSelectedAlgoCode = normalizeAlgoCode(selectedAlgorithm.algoCd);
    if (!normalizedSelectedAlgoCode) {
      setApplyErrorMessage('선택한 알고리즘 코드가 비어 있습니다.');
      return;
    }

    if (isCurrentSelectionApplied) {
      const persistedAlgoCode = activeSelection?.active_algo_code?.trim() || normalizedSelectedAlgoCode;
      const persistedAlgoName = activeSelection?.active_algo_name?.trim() || selectedAlgorithm.algoNm.trim() || persistedAlgoCode;
      const persistedDatasetKey = activeSelection?.dataset_key?.trim() || resolvedDatasetKey;
      const encodedDatasetKey = encodeURIComponent(persistedDatasetKey);
      persistOperationDatasetKey(persistedDatasetKey);

      setSelectedAlgorithm({
        algoCd: persistedAlgoCode,
        algoNm: persistedAlgoName,
      });

      navigate(`/operation/modeltrain?datasetKey=${encodedDatasetKey}`, {
        state: {
          algoCd: persistedAlgoCode,
          algoNm: persistedAlgoName,
          dataset_key: persistedDatasetKey,
          datasetKey: persistedDatasetKey,
          selectedDataset: persistedDatasetKey,
        },
      });
      return;
    }

    setApplyingSelection(true);
    setApplyErrorMessage(null);

    try {
      const savedSelection = await algorithmSelectionService.applySelection({
        dataset_key: resolvedDatasetKey,
        algo_code: normalizedSelectedAlgoCode,
        changed_by: user?.empcode ?? user?.empname ?? 'unknown',
        changed_reason: 'UI_ALGORITHM_SELECTION_APPLY',
      });

      const persistedAlgoCode = savedSelection.active_algo_code?.trim();
      if (!persistedAlgoCode) {
        throw new Error('적용 응답에 active_algo_code가 없습니다.');
      }
      const persistedAlgoName = savedSelection.active_algo_name?.trim() || persistedAlgoCode;
      const persistedDatasetKey = savedSelection.dataset_key?.trim() || resolvedDatasetKey;
      const encodedDatasetKey = encodeURIComponent(persistedDatasetKey);
      persistOperationDatasetKey(persistedDatasetKey);

      setSelectedAlgorithm({
        algoCd: persistedAlgoCode,
        algoNm: persistedAlgoName,
      });

      const nextActiveSelection: AlgorithmActiveSelectionData = {
        dataset_key: persistedDatasetKey,
        active_policy_id: savedSelection.active_policy_id,
        active_algo_code: persistedAlgoCode,
        active_algo_name: persistedAlgoName,
        updated_at: savedSelection.updated_at,
      };
      cacheActiveSelection(persistedDatasetKey, nextActiveSelection);

      const mappedSelection = findAlgorithmPointerByCode(selectionData, persistedAlgoCode);
      if (mappedSelection) {
        setSelectedTypeCd(mappedSelection.typeCd);
        setSelectedAlgoCd(mappedSelection.algoCd);
      }

      navigate(`/operation/modeltrain?datasetKey=${encodedDatasetKey}`, {
        state: {
          algoCd: persistedAlgoCode,
          algoNm: persistedAlgoName,
          dataset_key: persistedDatasetKey,
          datasetKey: persistedDatasetKey,
          selectedDataset: persistedDatasetKey,
        },
      });
    } catch (error: unknown) {
      setApplyErrorMessage(toApplyFailureMessage(error, normalizedSelectedAlgoCode));
    } finally {
      setApplyingSelection(false);
    }
  };

  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h4">알고리즘 선택</Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 0.5 }}>
          분석 목적별 추천 알고리즘을 비교하고, 다음 전처리 단계에 반영할 알고리즘을 확정합니다.
        </Typography>

        <Stack
          direction={{ xs: 'column', md: 'row' }}
          spacing={1}
          alignItems={{ xs: 'stretch', md: 'center' }}
          sx={{ mt: 1.2, maxWidth: 760 }}
        >
          <TextField
            select
            label="운영 Dataset"
            value={resolvedDatasetKey}
            onChange={(event) => {
              setSelectedDatasetKey(event.target.value);
              setApplyErrorMessage(null);
              setActiveSelectionLookupMessage(null);
            }}
            disabled={loadingDatasetOptions || datasetOptions.length === 0}
            fullWidth
          >
            {datasetOptions.map((option) => (
              <MenuItem key={option.datasetKey} value={option.datasetKey}>
                {resolveOperationDatasetLabel(option)} ({option.datasetKey})
              </MenuItem>
            ))}
          </TextField>
        </Stack>

        {activeSelection ? (
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1 }}>
            <Chip
              size="small"
              color="success"
              label={`현재 적용 알고리즘: ${activeSelection.active_algo_name ?? activeSelection.active_algo_code ?? '알 수 없음'}`}
            />
            <Chip size="small" variant="outlined" label={`dataset: ${resolvedDatasetKey}`} />
            {selectedDatasetOption && (
              <Chip size="small" variant="outlined" label={resolveOperationDatasetLabel(selectedDatasetOption)} />
            )}
          </Stack>
        ) : (
          <Chip
            size="small"
            color="warning"
            label={`현재 적용된 알고리즘 없음 (dataset: ${resolvedDatasetKey})`}
            sx={{ mt: 1 }}
          />
        )}
      </Box>

      {errorMessage && <Alert severity="error">{errorMessage}</Alert>}
      {datasetSelectionWarning && <Alert severity="warning">{datasetSelectionWarning}</Alert>}
      {activeSelectionLookupMessage && <Alert severity="warning">{activeSelectionLookupMessage}</Alert>}
      {activeSelectionMappingMessage && <Alert severity="warning">{activeSelectionMappingMessage}</Alert>}
      {randomForestGuideMessage && <Alert severity="info">{randomForestGuideMessage}</Alert>}
      {applyErrorMessage && <Alert severity="error">{applyErrorMessage}</Alert>}

      <Box
        sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: {
            xs: '1fr',
            lg: '240px minmax(0, 1fr) 320px',
          },
          alignItems: 'start',
        }}
      >
        <Card variant="outlined">
          <CardContent sx={{ p: 1.25 }}>
            <Typography variant="subtitle1" fontWeight={700} sx={{ px: 1, pb: 0.8 }}>
              분석 목적
            </Typography>
            <Divider sx={{ mb: 1 }} />
            {loading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                <CircularProgress size={28} />
              </Box>
            ) : (
              <List disablePadding>
                {algoTypes.map((type) => (
                  <ListItemButton
                    key={type.algoTypeCd}
                    selected={selectedTypeCd === type.algoTypeCd}
                    onClick={() => setSelectedTypeCd(type.algoTypeCd)}
                    sx={{
                      mb: 0.5,
                      borderRadius: 2,
                      '&.Mui-selected': {
                        backgroundColor: '#e7efff',
                        color: '#124aa8',
                      },
                    }}
                  >
                    <ListItemText
                      primary={type.algoTypeNm}
                      secondary={type.desc}
                      primaryTypographyProps={{ fontSize: 14, fontWeight: 700 }}
                      secondaryTypographyProps={{ fontSize: 12, sx: { mt: 0.3, whiteSpace: 'normal' } }}
                    />
                  </ListItemButton>
                ))}
              </List>
            )}
          </CardContent>
        </Card>

        <Card variant="outlined">
          <CardContent>
            <Typography variant="subtitle1" fontWeight={700}>
              추천 알고리즘
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.4, mb: 1.5 }}>
              {selectedType ? `${selectedType.algoTypeNm} 목적 기준 추천 목록` : '분석 목적을 선택하세요.'}
            </Typography>

            {loading && (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
                <CircularProgress size={30} />
              </Box>
            )}

            {!loading && currentAlgorithms.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                선택된 목적과 연결된 알고리즘이 없습니다.
              </Typography>
            )}

            {!loading && currentAlgorithms.length > 0 && (
              <Box
                sx={{
                  display: 'grid',
                  gap: 1.5,
                  gridTemplateColumns: {
                    xs: '1fr',
                    md: 'repeat(2, minmax(0, 1fr))',
                  },
                }}
              >
                {currentAlgorithms.map((algorithm) => {
                  const algorithmMeta = getAlgorithmMeta(algorithm.algoCd);
                  const selected = selectedAlgoCd === algorithm.algoCd;
                  return (
                    <Card
                      key={algorithm.algoCd}
                      variant="outlined"
                      sx={{
                        borderColor: selected ? '#1a5dcb' : '#d8dfeb',
                        backgroundColor: selected ? '#eef4ff' : '#ffffff',
                      }}
                    >
                      <CardActionArea onClick={() => setSelectedAlgoCd(algorithm.algoCd)}>
                        <CardContent>
                          <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
                            <Typography variant="subtitle2" fontWeight={700}>
                              {algorithm.algoNm}
                            </Typography>
                          </Stack>
                          <Typography variant="body2" color="text.secondary" sx={{ minHeight: 40 }}>
                            {algorithmMeta.summary}
                          </Typography>
                          <Stack direction="row" spacing={0.8} sx={{ mt: 1, flexWrap: 'wrap', rowGap: 0.8 }}>
                            {(['accuracy', 'trainSpeed', 'predictSpeed'] as const).map((key) => {
                              const value = algorithmMeta[key];
                              const style = getBadgeStyle(value);
                              return (
                                <Chip
                                  key={`${algorithm.algoCd}-${key}`}
                                  size="small"
                                  label={value}
                                  sx={{
                                    fontWeight: 700,
                                    backgroundColor: style.backgroundColor,
                                    color: style.color,
                                    border: `1px solid ${style.borderColor}`,
                                  }}
                                />
                              );
                            })}
                          </Stack>
                        </CardContent>
                      </CardActionArea>
                    </Card>
                  );
                })}
              </Box>
            )}
          </CardContent>
        </Card>

        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          <Card variant="outlined">
            <CardContent>
              <Typography variant="subtitle1" fontWeight={700}>
                비교 정보
              </Typography>
              {!selectedAlgorithm || !selectedMeta ? (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1.2 }}>
                  알고리즘을 선택하면 비교 지표가 표시됩니다.
                </Typography>
              ) : (
                <Stack spacing={1.1} sx={{ mt: 1.2 }}>
                  <Stack direction="row" alignItems="center" spacing={0.8}>
                    <PsychologyIcon color="primary" fontSize="small" />
                    <Typography variant="subtitle2" fontWeight={700}>
                      {selectedAlgorithm.algoNm}
                    </Typography>
                  </Stack>

                  <Typography variant="body2" color="text.secondary">
                    {selectedMeta.summary}
                  </Typography>

                  <Divider />

                  {METRIC_ITEMS.map((item) => {
                    const value = selectedMeta[item.key];
                    const style = getBadgeStyle(value);
                    return (
                      <Box
                        key={item.key}
                        sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
                      >
                        <Typography variant="body2" color="text.secondary">
                          {item.label}
                        </Typography>
                        <Chip
                          size="small"
                          label={value}
                          sx={{
                            fontWeight: 700,
                            minWidth: 72,
                            backgroundColor: style.backgroundColor,
                            color: style.color,
                            border: `1px solid ${style.borderColor}`,
                          }}
                        />
                      </Box>
                    );
                  })}
                </Stack>
              )}
            </CardContent>
          </Card>

          <Stack spacing={1}>
            <Alert severity="info" sx={{ py: 0.4 }}>
              파라미터 변경은 모델 학습 정책 화면에서 가능합니다.
            </Alert>
            <Button
              fullWidth
              variant={isCurrentSelectionApplied ? 'outlined' : 'contained'}
              color={isCurrentSelectionApplied ? 'success' : 'primary'}
              size="large"
              disabled={!selectedAlgoCd || applyingSelection}
              onClick={() => void handleApplyAndMoveToModelTrain()}
              sx={{
                height: 48,
                fontWeight: 700,
                borderRadius: 2,
              }}
            >
              {applyingSelection
                ? '적용 중...'
                : isCurrentSelectionApplied
                  ? '모델 학습 화면 이동'
                  : '선택 알고리즘 적용 후 모델 학습 이동'}
            </Button>
          </Stack>
        </Box>
      </Box>

      <Card
        variant="outlined"
        sx={{
          borderColor: '#c8d8f0',
          background: 'linear-gradient(120deg, #f7fbff 0%, #f2f8f3 100%)',
        }}
      >
        <CardContent>
          <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1.5 }}>
            <AutoAwesomeIcon color="primary" fontSize="small" />
            <Typography variant="subtitle1" fontWeight={700}>
              선택 알고리즘 설명
            </Typography>
          </Stack>

          {!selectedAlgorithm || !selectedMeta ? (
            <Typography variant="body2" color="text.secondary">
              선택된 알고리즘이 없습니다.
            </Typography>
          ) : (
            <Grid container spacing={2.5} alignItems="stretch">
              <Grid size={{ xs: 12, md: 4 }}>
                <Box
                  sx={{
                    width: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderRadius: 3,
                    overflow: 'hidden',
                    border: '1px solid #d6deea',
                    backgroundColor: '#0b1220',
                    p: 1,
                  }}
                >
                  <Box
                    component="img"
                    src={selectedVisualMeta.imageSrc}
                    alt={selectedVisualMeta.imageAlt}
                    sx={{
                      width: '100%',
                      maxWidth: '100%',
                      height: 'auto',
                      maxHeight: {
                        xs: 260,
                        sm: 320,
                        md: 'clamp(260px, 32vw, 520px)',
                      },
                      objectFit: 'contain',
                      display: 'block',
                    }}
                  />
                </Box>
              </Grid>

              <Grid size={{ xs: 12, md: 8 }}>
                <Stack spacing={2}>
                  <Box>
                    <Typography variant="h5" fontWeight={800}>
                      {selectedAlgorithm.algoNm}
                    </Typography>
                    <Typography variant="subtitle1" color="primary" sx={{ mt: 0.5, fontWeight: 700 }}>
                      {selectedVisualMeta.overview}
                    </Typography>
                  </Box>

                  <Typography variant="body1" color="text.secondary" sx={{ lineHeight: 1.8 }}>
                    {selectedVisualMeta.detail}
                  </Typography>

                  <Box
                    sx={{
                      display: 'grid',
                      gap: 1,
                      gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' },
                    }}
                  >
                    <Card variant="outlined" sx={{ borderColor: '#d6deea', borderRadius: 1 }}>
                      <CardContent>
                        <Typography variant="subtitle2" fontWeight={800} sx={{ mb: 1 }}>
                          핵심 장점
                        </Typography>
                        <Stack spacing={0.8}>
                          {selectedVisualMeta.strengths.map((item) => (
                            <Typography key={item} variant="body2" color="text.secondary">
                              • {item}
                            </Typography>
                          ))}
                        </Stack>
                      </CardContent>
                    </Card>

                    <Card variant="outlined" sx={{ borderColor: '#d6deea', borderRadius: 1 }}>
                      <CardContent>
                        <Typography variant="subtitle2" fontWeight={800} sx={{ mb: 1 }}>
                          추천 사용 상황
                        </Typography>
                        <Stack spacing={0.8}>
                          {selectedVisualMeta.recommendedFor.map((item) => (
                            <Typography key={item} variant="body2" color="text.secondary">
                              • {item}
                            </Typography>
                          ))}
                        </Stack>
                      </CardContent>
                    </Card>
                  </Box>

                  <Box
                    sx={{
                      display: 'grid',
                      gap: 1,
                      gridTemplateColumns: {
                        xs: '1fr',
                        sm: 'repeat(2, minmax(0, 1fr))',
                        lg: 'repeat(5, minmax(0, 1fr))',
                      },
                    }}
                  >
                    {METRIC_ITEMS.map((item) => {
                      const value = selectedMeta[item.key];
                      const style = getBadgeStyle(value);
                      return (
                        <Box
                          key={`summary-metric-${item.key}`}
                          sx={{
                            px: 1.5,
                            py: 1.2,
                            display: 'flex',
                            alignItems: 'center',
                            gap: 1.5,
                            border: '1px solid #d6deea',
                            borderRadius: 1,
                            backgroundColor: '#fff',
                          }}
                        >
                          <Typography variant="caption" color="text.secondary">
                            {item.label}
                          </Typography>
                          <Chip
                            size="small"
                            label={value}
                            sx={{
                              mt: 0.8,
                              fontWeight: 700,
                              minWidth: 72,
                              backgroundColor: style.backgroundColor,
                              color: style.color,
                              border: `1px solid ${style.borderColor}`,
                            }}
                          />
                        </Box>
                      );
                    })}
                  </Box>
                </Stack>
              </Grid>
            </Grid>
          )}
        </CardContent>
      </Card>

    </Stack>
  );
}
