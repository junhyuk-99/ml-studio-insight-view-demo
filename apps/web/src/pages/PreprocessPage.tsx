import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import TuneIcon from '@mui/icons-material/Tune';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  FormGroup,
  List,
  ListItemButton,
  ListItemText,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  getPreprocessPolicyByPrepType,
  PREPROCESS_STATUS_LABEL,
  type PreprocessOptionStatus,
} from '../constants/preprocessPolicy';
import { preprocessService } from '../services/preprocessService';
import { useSelectedAlgorithm } from '../store/SelectedAlgorithmContext';
import type { DataSourceDataset, DataSourceType, PreprocessOptionData, RawDataPreviewData } from '../types/preprocess';
import { persistOperationDatasetKey } from '../utils/operationDataset';

const PREVIEW_LIMIT = 200;

const STATUS_VISUAL: Record<
  PreprocessOptionStatus,
  {
    chipColor: 'default' | 'success' | 'info' | 'warning';
    backgroundColor: string;
    borderColor: string;
    opacity: number;
  }
> = {
  recommended: {
    chipColor: 'success',
    backgroundColor: '#f4fbf6',
    borderColor: '#c6e6d0',
    opacity: 1,
  },
  optional: {
    chipColor: 'info',
    backgroundColor: '#f7fbff',
    borderColor: '#d4e3f5',
    opacity: 1,
  },
  discouraged: {
    chipColor: 'warning',
    backgroundColor: '#fffaf2',
    borderColor: '#f1dfbb',
    opacity: 0.92,
  },
  disabled: {
    chipColor: 'default',
    backgroundColor: '#f6f6f6',
    borderColor: '#dbdbdb',
    opacity: 0.65,
  },
};

function formatCellValue(value: unknown): string {
  if (value === null || value === undefined) {
    return '-';
  }

  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }

  try {
    return JSON.stringify(value);
  } catch (_error) {
    return String(value);
  }
}

export function PreprocessPage() {
  const navigate = useNavigate();
  const { selectedAlgorithm } = useSelectedAlgorithm();

  const [dataSources, setDataSources] = useState<DataSourceType[]>([]);
  const [selectedTypeCode, setSelectedTypeCode] = useState('');
  const [selectedDtlCode, setSelectedDtlCode] = useState('');
  const [expandedTypeCode, setExpandedTypeCode] = useState<string | null>(null);

  const [previewData, setPreviewData] = useState<RawDataPreviewData | null>(null);
  const [selectedColumns, setSelectedColumns] = useState<string[]>([]);
  const [selectedDatasetKey, setSelectedDatasetKey] = useState('');

  const [optionData, setOptionData] = useState<PreprocessOptionData | null>(null);
  const [selectedPrepCodes, setSelectedPrepCodes] = useState<string[]>([]);
  const [isOptionDialogOpen, setIsOptionDialogOpen] = useState(false);

  const [loadingSources, setLoadingSources] = useState(false);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [loadingOptions, setLoadingOptions] = useState(false);

  const [sourceError, setSourceError] = useState<string | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [optionError, setOptionError] = useState<string | null>(null);

  const optionPolicyByPrepType = useMemo(() => {
    return getPreprocessPolicyByPrepType(selectedAlgorithm?.algoCd);
  }, [selectedAlgorithm]);

  useEffect(() => {
    setLoadingSources(true);
    setLoadingOptions(true);
    setSourceError(null);
    setOptionError(null);

    preprocessService
      .getDataSources()
      .then((data) => {
        const dataTypes = data.dataTypes ?? [];
        setDataSources(dataTypes);
        const firstDatasetNode = findFirstDatasetNode(dataTypes);
        if (firstDatasetNode) {
          setSelectedTypeCode(firstDatasetNode.typeCode);
          setSelectedDtlCode(firstDatasetNode.dtlCode);
          setSelectedDatasetKey(firstDatasetNode.dataset.datasetKey);
          setExpandedTypeCode(firstDatasetNode.typeCode);
          return;
        }
        setSelectedTypeCode('');
        setSelectedDtlCode('');
        setSelectedDatasetKey('');
        setExpandedTypeCode(null);
      })
      .catch((error: unknown) => {
        setSourceError(error instanceof Error ? error.message : '데이터 소스 목록을 불러오지 못했습니다.');
      })
      .finally(() => {
        setLoadingSources(false);
      });

    preprocessService
      .getPreprocessOptions()
      .then((data) => {
        setOptionData(data);
      })
      .catch((error: unknown) => {
        setOptionError(error instanceof Error ? error.message : '전처리 옵션을 불러오지 못했습니다.');
      })
      .finally(() => {
        setLoadingOptions(false);
      });
  }, []);

  useEffect(() => {
    setLoadingPreview(true);
    setPreviewError(null);

    preprocessService
      .getRawPreview(
        selectedDatasetKey.trim().length > 0
          ? { datasetKey: selectedDatasetKey, limit: PREVIEW_LIMIT }
          : { typeCode: selectedTypeCode, dtlCode: selectedDtlCode, limit: PREVIEW_LIMIT },
      )
      .then((data) => {
        setPreviewData(data);
        setSelectedColumns((previousColumns) => {
          const previousInRange = previousColumns.filter((column) => data.availableColumns.includes(column));
          return previousInRange.length > 0 ? previousInRange : data.availableColumns;
        });
      })
      .catch((error: unknown) => {
        setPreviewError(error instanceof Error ? error.message : '원시 데이터 미리보기를 불러오지 못했습니다.');
        setPreviewData(null);
        setSelectedColumns([]);
      })
      .finally(() => {
        setLoadingPreview(false);
      });
  }, [selectedDatasetKey, selectedTypeCode, selectedDtlCode]);

  const hasSourceSelection =
    selectedDatasetKey.trim().length > 0 || selectedTypeCode.trim().length > 0 || selectedDtlCode.trim().length > 0;

  useEffect(() => {
    const normalizedDatasetKey = selectedDatasetKey.trim();
    if (!normalizedDatasetKey) {
      return;
    }
    persistOperationDatasetKey(normalizedDatasetKey);
  }, [selectedDatasetKey]);

  useEffect(() => {
    if (!optionData) {
      setSelectedPrepCodes([]);
      return;
    }

    const enabledCodes = new Set<string>();

    optionData.prepTypes.forEach((prepType) => {
      const policy = optionPolicyByPrepType[prepType.prepTypeCd];
      if (policy?.status === 'disabled') {
        return;
      }

      const options = optionData.optionsByType[prepType.prepTypeCd] ?? [];
      options.forEach((option) => enabledCodes.add(option.prepCd));
    });

    setSelectedPrepCodes((previousCodes) => previousCodes.filter((code) => enabledCodes.has(code)));
  }, [optionData, optionPolicyByPrepType]);

  const selectedDetail = useMemo(() => {
    const type = dataSources.find((item) => item.typeCode === selectedTypeCode);
    if (!type) {
      return null;
    }

    const detail = type.details.find((item) => item.dtlCode === selectedDtlCode);
    if (!detail) {
      return null;
    }

    return { type, detail };
  }, [dataSources, selectedTypeCode, selectedDtlCode]);

  const selectedDataset = useMemo(() => {
    if (!selectedDetail) {
      return null;
    }
    return selectedDetail.detail.datasets?.find((dataset) => dataset.datasetKey === selectedDatasetKey) ?? null;
  }, [selectedDatasetKey, selectedDetail]);

  const selectedDatasetLabel = useMemo(() => buildDatasetStatusLabel(selectedDataset), [selectedDataset]);

  const visibleColumns = useMemo(() => {
    if (!previewData) {
      return [] as string[];
    }

    return previewData.availableColumns.filter((column) => selectedColumns.includes(column));
  }, [previewData, selectedColumns]);

  const selectDataDetail = (typeCode: string, dtlCode: string) => {
    setSelectedTypeCode(typeCode);
    setSelectedDtlCode(dtlCode);
    setExpandedTypeCode(typeCode);
    const targetType = dataSources.find((item) => item.typeCode === typeCode);
    const targetDetail = targetType?.details.find((item) => item.dtlCode === dtlCode);
    setSelectedDatasetKey(targetDetail?.datasets?.[0]?.datasetKey ?? '');
  };

  const selectDataset = (typeCode: string, dtlCode: string, datasetKey: string) => {
    setSelectedTypeCode(typeCode);
    setSelectedDtlCode(dtlCode);
    setSelectedDatasetKey(datasetKey);
    setExpandedTypeCode(typeCode);
  };

  const toggleDataSourceType = (type: DataSourceType, isExpanded: boolean) => {
    setExpandedTypeCode(isExpanded ? type.typeCode : null);
  };

  const toggleColumn = (column: string) => {
    setSelectedColumns((previousColumns) => {
      if (!previewData) {
        return previousColumns;
      }

      if (previousColumns.includes(column)) {
        return previousColumns.filter((item) => item !== column);
      }

      const nextColumns = [...previousColumns, column];
      return previewData.availableColumns.filter((item) => nextColumns.includes(item));
    });
  };

  const togglePreprocessOption = (prepCd: string, disabled: boolean) => {
    if (disabled) {
      return;
    }

    setSelectedPrepCodes((previousCodes) => {
      if (previousCodes.includes(prepCd)) {
        return previousCodes.filter((code) => code !== prepCd);
      }

      return [...previousCodes, prepCd];
    });
  };

  const moveToAlgorithmSelection = () => {
    const normalizedDatasetKey = selectedDatasetKey.trim();
    if (normalizedDatasetKey) {
      persistOperationDatasetKey(normalizedDatasetKey);
    }

    const query = normalizedDatasetKey
      ? `?datasetKey=${encodeURIComponent(normalizedDatasetKey)}`
      : '';

    navigate(`/operation/algorithm${query}`, {
      state: {
        dataset_key: normalizedDatasetKey,
        datasetKey: normalizedDatasetKey,
        selectedDataset: normalizedDatasetKey,
      },
    });
  };

  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h4">Preprocess / Feature Engineering</Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 0.5 }}>
          Synthetic demo workflow for DEMO_DATASET_MANUFACTURING_AI: raw preview, numeric feature selection, window stats, and feature preview.
        </Typography>
      </Box>

      <Alert severity="info">
        Synthetic demo data only. Raw rows use THISHMIDATA and feature previews use thisfeature or frontend fallback samples.
      </Alert>

      {selectedAlgorithm ? (
        <Alert
          severity="info"
          action={
            <Button size="small" onClick={moveToAlgorithmSelection}>
              알고리즘 변경
            </Button>
          }
        >
          현재 선택 알고리즘: <strong>{selectedAlgorithm.algoNm}</strong> ({selectedAlgorithm.algoCd})
        </Alert>
      ) : (
        <Alert
          severity="warning"
          action={
            <Button size="small" variant="outlined" onClick={moveToAlgorithmSelection}>
              알고리즘 선택 이동
            </Button>
          }
        >
          아직 반영된 알고리즘이 없습니다. 알고리즘 선택 화면에서 적용 후 이동하면 알고리즘별 옵션 정책이 활성화됩니다.
        </Alert>
      )}

      <Box
        sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: {
            xs: '1fr',
            lg: '250px minmax(0, 1fr) 320px',
          },
          alignItems: 'start',
        }}
      >
        <Card variant="outlined">
          <CardContent sx={{ p: 1.25 }}>
            <Typography variant="subtitle1" fontWeight={700} sx={{ px: 1, pb: 0.8 }}>
              데이터 소스
            </Typography>
            <Divider sx={{ mb: 1 }} />

            {loadingSources && (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
                <CircularProgress size={28} />
              </Box>
            )}

            {!loadingSources && sourceError && <Alert severity="error">{sourceError}</Alert>}

            {!loadingSources && !sourceError && dataSources.length === 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ px: 1 }}>
                활성화된 데이터 소스가 없습니다.
              </Typography>
            )}

            {!loadingSources && !sourceError && dataSources.length > 0 && (
              <Stack spacing={1}>
                {dataSources.map((type) => (
                  <Accordion
                    key={type.typeCode}
                    disableGutters
                    expanded={expandedTypeCode === type.typeCode}
                    onChange={(_event, isExpanded) => toggleDataSourceType(type, isExpanded)}
                    sx={{
                      border: '1px solid #d6deea',
                      borderRadius: 2,
                      overflow: 'hidden',
                      '&:before': { display: 'none' },
                    }}
                  >
                    <AccordionSummary
                      expandIcon={<ExpandMoreIcon />}
                      sx={{
                        minHeight: 46,
                        '& .MuiAccordionSummary-content': { my: 1 },
                        ...(selectedTypeCode === type.typeCode && {
                          backgroundColor: '#edf4ff',
                          color: '#11489f',
                          borderRadius: '8px 8px 0 0',
                        }),
                      }}
                    >
                      <Stack direction="row" spacing={1} alignItems="center" sx={{ width: '100%' }}>
                        <Chip
                          label={type.typeCode}
                          size="small"
                          color={selectedTypeCode === type.typeCode ? 'primary' : 'default'}
                        />
                        <Typography variant="subtitle2" fontWeight={700}>
                          {type.typeName}
                        </Typography>
                      </Stack>
                    </AccordionSummary>

                    <AccordionDetails sx={{ py: 0.5 }}>
                      <List disablePadding>
                        {type.details.map((detail) => {
                          const detailDatasets = detail.datasets ?? [];
                          const hasDatasets = detailDatasets.length > 0;
                          const isDetailSelected = selectedTypeCode === type.typeCode && selectedDtlCode === detail.dtlCode;

                          return (
                            <Box key={`${type.typeCode}-${detail.dtlCode}`} sx={{ mb: 0.5 }}>
                              <ListItemButton
                                selected={isDetailSelected && !hasDatasets}
                                onClick={() => selectDataDetail(type.typeCode, detail.dtlCode)}
                                sx={{
                                  borderRadius: 1.5,
                                  '&.Mui-selected': {
                                    backgroundColor: '#e7efff',
                                    color: '#11489f',
                                    borderRadius: 1.5,
                                  },
                                }}
                              >
                                <ListItemText
                                  primary={detail.dtlName}
                                  secondary={detail.dtlCode}
                                  primaryTypographyProps={{ fontSize: 13, fontWeight: 700 }}
                                  secondaryTypographyProps={{ fontSize: 11 }}
                                />
                              </ListItemButton>

                              {hasDatasets && (
                                <List disablePadding sx={{ pl: 1.5, mt: 0.3 }}>
                                  {detailDatasets.map((dataset) => (
                                    <ListItemButton
                                      key={`${type.typeCode}-${detail.dtlCode}-${dataset.datasetKey}`}
                                      selected={isDetailSelected && selectedDatasetKey === dataset.datasetKey}
                                      onClick={() => selectDataset(type.typeCode, detail.dtlCode, dataset.datasetKey)}
                                      sx={{
                                        borderRadius: 1.5,
                                        mb: 0.4,
                                        minHeight: 36,
                                        '&.Mui-selected': {
                                          backgroundColor: '#e7efff',
                                          color: '#11489f',
                                          borderRadius: 1.5,
                                        },
                                      }}
                                    >
                                      <ListItemText
                                        primary={dataset.displayName ?? dataset.datasetName ?? dataset.datasetKey}
                                        secondary={dataset.datasetName ?? dataset.datasetKey}
                                        primaryTypographyProps={{ fontSize: 12.5, fontWeight: 700 }}
                                        secondaryTypographyProps={{ fontSize: 11 }}
                                      />
                                    </ListItemButton>
                                  ))}
                                </List>
                              )}
                            </Box>
                          );
                        })}
                      </List>
                    </AccordionDetails>
                  </Accordion>
                ))}
              </Stack>
            )}
          </CardContent>
        </Card>

        <Card variant="outlined">
          <CardContent>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ xs: 'flex-start', sm: 'center' }}>
              <Typography variant="subtitle1" fontWeight={700}>
                원시 데이터 미리보기
              </Typography>
              {selectedDetail && (
                <>
                  <Chip size="small" label={`유형: ${selectedDetail.type.typeCode}`} />
                  <Chip size="small" label={`상세: ${selectedDetail.detail.dtlCode}`} />
                </>
              )}
              {selectedDatasetLabel && <Chip size="small" label={`dataset: ${selectedDatasetLabel}`} />}
            </Stack>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.8 }}>
              최대 행 수: {PREVIEW_LIMIT}
            </Typography>

            <Divider sx={{ my: 1.5 }} />

            {loadingPreview && (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 7 }}>
                <CircularProgress size={30} />
              </Box>
            )}

            {!loadingPreview && previewError && <Alert severity="error">{previewError}</Alert>}

            {!loadingPreview && !previewError && previewData && previewData.rawRows.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                {hasSourceSelection ? '선택한 소스 조건에 해당하는 데이터가 없습니다.' : '표시할 원시 데이터가 없습니다.'}
              </Typography>
            )}

            {!loadingPreview && !previewError && previewData && previewData.rawRows.length > 0 && visibleColumns.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                오른쪽 패널에서 컬럼을 선택하면 테이블이 표시됩니다.
              </Typography>
            )}

            {!loadingPreview && !previewError && previewData && previewData.rawRows.length > 0 && visibleColumns.length > 0 && (
              <TableContainer sx={{ maxHeight: 560, border: '1px solid #d6deea', borderRadius: 2 }}>
                <Table stickyHeader size="small">
                  <TableHead>
                    <TableRow>
                      {visibleColumns.map((column) => (
                        <TableCell key={column} sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>
                          {column}
                        </TableCell>
                      ))}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {previewData.rawRows.map((row, rowIndex) => (
                      <TableRow key={`${selectedTypeCode}-${selectedDtlCode}-${selectedDatasetKey}-${rowIndex}`} hover>
                        {visibleColumns.map((column) => (
                          <TableCell key={`${rowIndex}-${column}`} sx={{ whiteSpace: 'nowrap' }}>
                            {formatCellValue(row[column])}
                          </TableCell>
                        ))}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </CardContent>
        </Card>

        <Stack spacing={2}>
          <Card variant="outlined">
            <CardContent>
              <Typography variant="subtitle1" fontWeight={700}>
                전처리 옵션
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.8, mb: 1.2 }}>
                알고리즘 정책 기준으로 옵션 상태를 확인할 수 있습니다.
              </Typography>
              <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap" useFlexGap>
                <Button
                  variant="contained"
                  size="small"
                  startIcon={<TuneIcon />}
                  onClick={() => setIsOptionDialogOpen(true)}
                >
                  옵션 열기
                </Button>
                <Chip size="small" label={`${selectedPrepCodes.length}개 선택됨`} />
                {selectedAlgorithm && (
                  <Chip size="small" color="primary" label={`기준: ${selectedAlgorithm.algoNm}`} />
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card variant="outlined">
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Typography variant="subtitle1" fontWeight={700}>
                  컬럼 선택
                </Typography>
                {previewData && (
                  <Typography variant="caption" color="text.secondary">
                    {visibleColumns.length} / {previewData.availableColumns.length}
                  </Typography>
                )}
              </Stack>

              <Divider sx={{ my: 1.2 }} />

              {!previewData && (
                <Typography variant="body2" color="text.secondary">
                  원시 데이터 미리보기가 로드되면 표시할 컬럼을 선택할 수 있습니다.
                </Typography>
              )}

              {previewData && (
                <Stack spacing={1.2}>
                  <Stack direction="row" spacing={1}>
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => setSelectedColumns(previewData.availableColumns)}
                    >
                      전체 선택
                    </Button>
                    <Button size="small" variant="text" onClick={() => setSelectedColumns([])}>
                      초기화
                    </Button>
                  </Stack>

                  <Box
                    sx={{
                      maxHeight: 560,
                      overflowY: 'auto',
                      border: '1px solid #d6deea',
                      borderRadius: 2,
                      p: 1,
                    }}
                  >
                    <FormGroup>
                      {previewData.availableColumns.map((column) => (
                        <FormControlLabel
                          key={column}
                          control={
                            <Checkbox
                              size="small"
                              checked={selectedColumns.includes(column)}
                              onChange={() => toggleColumn(column)}
                            />
                          }
                          label={column}
                        />
                      ))}
                    </FormGroup>
                  </Box>
                </Stack>
              )}
            </CardContent>
          </Card>
        </Stack>
      </Box>

      <Dialog open={isOptionDialogOpen} onClose={() => setIsOptionDialogOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>
          전처리 옵션
          {selectedAlgorithm ? ` - ${selectedAlgorithm.algoNm}` : ''}
        </DialogTitle>
        <DialogContent dividers>
          {loadingOptions && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 5 }}>
              <CircularProgress size={30} />
            </Box>
          )}

          {!loadingOptions && optionError && <Alert severity="error">{optionError}</Alert>}

          {!loadingOptions && !optionError && optionData && optionData.prepTypes.length === 0 && (
            <Typography variant="body2" color="text.secondary">
              활성화된 전처리 옵션이 없습니다.
            </Typography>
          )}

          {!loadingOptions && !optionError && optionData && optionData.prepTypes.length > 0 && (
            <Stack spacing={1.2}>
              {optionData.prepTypes.map((prepType) => {
                const options = optionData.optionsByType[prepType.prepTypeCd] ?? [];
                const policy = optionPolicyByPrepType[prepType.prepTypeCd] ?? {
                  status: 'optional' as const,
                  reason: '기본 선택 가능 상태입니다.',
                };
                const statusVisual = STATUS_VISUAL[policy.status];
                const disabled = policy.status === 'disabled';

                return (
                  <Box
                    key={prepType.prepTypeCd}
                    sx={{
                      border: `1px solid ${statusVisual.borderColor}`,
                      borderRadius: 2,
                      p: 1.2,
                      backgroundColor: statusVisual.backgroundColor,
                      opacity: statusVisual.opacity,
                    }}
                  >
                    <Stack direction="row" justifyContent="space-between" alignItems="center" flexWrap="wrap" useFlexGap gap={0.8}>
                      <Typography variant="subtitle2" fontWeight={700}>
                        {prepType.prepTypeNm}
                      </Typography>
                      <Stack direction="row" spacing={0.8}>
                        <Chip size="small" color={statusVisual.chipColor} label={PREPROCESS_STATUS_LABEL[policy.status]} />
                        <Chip size="small" label={`${options.length}개`} />
                      </Stack>
                    </Stack>

                    {prepType.desc && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                        {prepType.desc}
                      </Typography>
                    )}

                    <Typography variant="caption" sx={{ display: 'block', mt: 0.4, color: '#4f5f76' }}>
                      정책 사유: {policy.reason}
                    </Typography>

                    {options.length === 0 && (
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.8 }}>
                        이 그룹에 활성화된 상세 옵션이 없습니다.
                      </Typography>
                    )}

                    {options.length > 0 && (
                      <FormGroup sx={{ mt: 0.8 }}>
                        {options.map((option) => (
                          <FormControlLabel
                            key={`${prepType.prepTypeCd}-${option.prepCd}`}
                            control={
                              <Checkbox
                                size="small"
                                checked={selectedPrepCodes.includes(option.prepCd)}
                                disabled={disabled}
                                onChange={() => togglePreprocessOption(option.prepCd, disabled)}
                              />
                            }
                            label={
                              <Stack direction="row" spacing={0.6} alignItems="center" flexWrap="wrap" useFlexGap>
                                <span>{option.prepNm}</span>
                                {policy.status === 'discouraged' && (
                                  <Chip size="small" variant="outlined" color="warning" label="비권장" />
                                )}
                                {policy.status === 'disabled' && (
                                  <Chip size="small" variant="outlined" label="비활성" />
                                )}
                              </Stack>
                            }
                          />
                        ))}
                      </FormGroup>
                    )}
                  </Box>
                );
              })}
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setIsOptionDialogOpen(false)}>닫기</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

function findFirstDatasetNode(dataSources: DataSourceType[]): {
  typeCode: string;
  dtlCode: string;
  dataset: DataSourceDataset;
} | null {
  for (const type of dataSources) {
    for (const detail of type.details) {
      const datasets = detail.datasets ?? [];
      if (datasets.length > 0) {
        return {
          typeCode: type.typeCode,
          dtlCode: detail.dtlCode,
          dataset: datasets[0],
        };
      }
    }
  }
  return null;
}

function normalizeOptionalText(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function buildDatasetStatusLabel(dataset: DataSourceDataset | null): string | null {
  if (!dataset) {
    return null;
  }

  const datasetName = normalizeOptionalText(dataset.datasetName);
  const displayName = normalizeOptionalText(dataset.displayName);

  if (datasetName && displayName) {
    return `${datasetName} (${displayName})`;
  }
  if (datasetName) {
    return datasetName;
  }
  if (displayName) {
    return displayName;
  }
  return normalizeOptionalText(dataset.datasetKey);
}
