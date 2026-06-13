import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { algorithmParamService } from '../../services/algorithmParamService';
import type {
  AlgorithmParamItem,
  AlgorithmParamSaveItem,
  AlgorithmParamSaveRequest,
} from '../../types/algorithmParam';

type AlgorithmParamDialogProps = {
  open: boolean;
  onClose: () => void;
  algoCd: string;
  algoNm?: string | null;
};

type ParamFormValue = {
  defaultValue: string;
  minValue: string;
  maxValue: string;
  uiType: string;
  step: string;
};

type ParamFormMap = Record<string, ParamFormValue>;

function normalizeDataType(dataType: string) {
  return dataType.trim().toLowerCase();
}

function isNumericType(dataType: string) {
  const normalized = normalizeDataType(dataType);
  return ['int', 'integer', 'long', 'short', 'float', 'double', 'decimal', 'number'].includes(normalized);
}

function isIntegerType(dataType: string) {
  const normalized = normalizeDataType(dataType);
  return ['int', 'integer', 'long', 'short'].includes(normalized);
}

function toStringValue(value: string | number | null | undefined) {
  if (value === null || value === undefined) {
    return '';
  }

  return String(value);
}

function buildInitialValues(params: AlgorithmParamItem[]): ParamFormMap {
  return params.reduce<ParamFormMap>((accumulator, param) => {
    accumulator[param.paramCd] = {
      defaultValue: toStringValue(param.defaultValue),
      minValue: toStringValue(param.minValue),
      maxValue: toStringValue(param.maxValue),
      uiType: toStringValue(param.uiType),
      step: toStringValue(param.step),
    };
    return accumulator;
  }, {});
}

function parseOptionalNumber(value: string, integerOnly: boolean): number | null {
  const trimmed = value.trim();
  if (trimmed.length === 0) {
    return null;
  }

  const parsed = Number(trimmed);
  if (!Number.isFinite(parsed)) {
    throw new Error('NUMBER_FORMAT');
  }

  if (integerOnly && !Number.isInteger(parsed)) {
    throw new Error('INTEGER_REQUIRED');
  }

  return parsed;
}

function validateParamForm(param: AlgorithmParamItem, form: ParamFormValue): string | null {
  const defaultText = form.defaultValue.trim();
  if (param.requiredYn === 'Y' && defaultText.length === 0) {
    return 'Required parameter must have a default value.';
  }

  if (!isNumericType(param.dataType)) {
    return null;
  }

  const integerOnly = isIntegerType(param.dataType);

  let defaultNumber: number | null;
  let minNumber: number | null;
  let maxNumber: number | null;

  try {
    defaultNumber = parseOptionalNumber(form.defaultValue, integerOnly);
  } catch (_error) {
    return integerOnly
      ? 'Default value must be an integer.'
      : 'Default value must be a valid number.';
  }

  try {
    minNumber = parseOptionalNumber(form.minValue, integerOnly);
  } catch (_error) {
    return integerOnly ? 'Min value must be an integer.' : 'Min value must be a valid number.';
  }

  try {
    maxNumber = parseOptionalNumber(form.maxValue, integerOnly);
  } catch (_error) {
    return integerOnly ? 'Max value must be an integer.' : 'Max value must be a valid number.';
  }

  try {
    parseOptionalNumber(form.step, integerOnly);
  } catch (_error) {
    return integerOnly ? 'Step must be an integer.' : 'Step must be a valid number.';
  }

  if (minNumber !== null && maxNumber !== null && minNumber > maxNumber) {
    return 'Min value cannot be greater than max value.';
  }

  if (defaultNumber !== null && minNumber !== null && defaultNumber < minNumber) {
    return 'Default value must be greater than or equal to min value.';
  }

  if (defaultNumber !== null && maxNumber !== null && defaultNumber > maxNumber) {
    return 'Default value must be less than or equal to max value.';
  }

  return null;
}

function toSaveValue(param: AlgorithmParamItem, form: ParamFormValue): AlgorithmParamSaveItem {
  const numericType = isNumericType(param.dataType);
  const integerOnly = isIntegerType(param.dataType);

  const normalizeText = (value: string) => {
    const trimmed = value.trim();
    return trimmed.length === 0 ? null : trimmed;
  };

  const toNumberOrNull = (value: string) => {
    const parsed = parseOptionalNumber(value, integerOnly);
    if (parsed === null) {
      return null;
    }
    return integerOnly ? Math.trunc(parsed) : parsed;
  };

  const defaultValue = numericType
    ? toNumberOrNull(form.defaultValue)
    : normalizeText(form.defaultValue);

  const minValue = numericType ? toNumberOrNull(form.minValue) : null;
  const maxValue = numericType ? toNumberOrNull(form.maxValue) : null;
  const step = numericType ? toNumberOrNull(form.step) : null;

  return {
    paramCd: param.paramCd,
    defaultValue,
    minValue,
    maxValue,
    uiType: normalizeText(form.uiType),
    step,
  };
}

export function AlgorithmParamDialog({ open, onClose, algoCd, algoNm }: AlgorithmParamDialogProps) {
  const [params, setParams] = useState<AlgorithmParamItem[]>([]);
  const [formValues, setFormValues] = useState<ParamFormMap>({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const loadParams = useCallback(async () => {
    if (!algoCd) {
      setParams([]);
      setFormValues({});
      return;
    }

    setLoading(true);
    setErrorMessage(null);

    try {
      const data = await algorithmParamService.getParams(algoCd);
      setParams(data);
      setFormValues(buildInitialValues(data));
    } catch (error: unknown) {
      setParams([]);
      setFormValues({});
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load algorithm parameters.');
    } finally {
      setLoading(false);
    }
  }, [algoCd]);

  useEffect(() => {
    if (!open) {
      return;
    }

    setSuccessMessage(null);
    loadParams();
  }, [open, loadParams]);

  const fieldErrors = useMemo(() => {
    return params.reduce<Record<string, string | null>>((accumulator, param) => {
      const form = formValues[param.paramCd] ?? {
        defaultValue: '',
        minValue: '',
        maxValue: '',
        uiType: '',
        step: '',
      };
      accumulator[param.paramCd] = validateParamForm(param, form);
      return accumulator;
    }, {});
  }, [params, formValues]);

  const hasValidationError = useMemo(() => {
    return Object.values(fieldErrors).some((error) => error !== null);
  }, [fieldErrors]);

  const canSave = !loading && !saving && !errorMessage && params.length > 0 && !hasValidationError;
  const dialogAlgoName = algoNm?.trim() || algoCd.trim();

  const handleFieldChange = (paramCd: string, field: keyof ParamFormValue, value: string) => {
    setSuccessMessage(null);
    setFormValues((previous) => ({
      ...previous,
      [paramCd]: {
        defaultValue: previous[paramCd]?.defaultValue ?? '',
        minValue: previous[paramCd]?.minValue ?? '',
        maxValue: previous[paramCd]?.maxValue ?? '',
        uiType: previous[paramCd]?.uiType ?? '',
        step: previous[paramCd]?.step ?? '',
        [field]: value,
      },
    }));
  };

  const handleSave = async () => {
    if (!canSave) {
      return;
    }

    setSaving(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const payload: AlgorithmParamSaveRequest = {
        algoCd,
        params: params.map((param) =>
          toSaveValue(param, formValues[param.paramCd] ?? {
            defaultValue: '',
            minValue: '',
            maxValue: '',
            uiType: '',
            step: '',
          }),
        ),
      };

      const response = await algorithmParamService.saveParams(payload);
      setSuccessMessage(`Saved ${response.savedCount} parameter(s).`);
      // await loadParams();  // 제거
    } catch (error: unknown) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to save parameters.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>{`${dialogAlgoName} Parameter Settings`}</DialogTitle>

      <DialogContent dividers>
        {loading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress size={28} />
          </Box>
        )}

        {!loading && errorMessage && <Alert severity="error">{errorMessage}</Alert>}
        {!loading && successMessage && <Alert severity="success">{successMessage}</Alert>}

        {!loading && !errorMessage && params.length === 0 && (
          <Alert severity="info">No parameters configured for selected algorithm.</Alert>
        )}

        {!loading && !errorMessage && params.length > 0 && (
          <Stack spacing={1.5}>
            {params.map((param) => {
              const form = formValues[param.paramCd] ?? {
                defaultValue: '',
                minValue: '',
                maxValue: '',
                uiType: '',
                step: '',
              };

              const fieldError = fieldErrors[param.paramCd];
              const numericType = isNumericType(param.dataType);
              const rangeText = numericType
                ? `Range: ${form.minValue.trim() || '-'} ~ ${form.maxValue.trim() || '-'}`
                : 'Range: -';

              return (
                <Box
                  key={param.paramCd}
                  sx={{
                    border: '1px solid #d6deea',
                    borderRadius: 2,
                    p: 1.5,
                  }}
                >
                  <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
                    <Typography variant="subtitle2" fontWeight={700}>
                      {param.paramNm}
                    </Typography>
                    <Chip size="small" label={param.paramCd} />
                    <Chip size="small" label={param.dataType} color="primary" variant="outlined" />
                    {param.requiredYn === 'Y' && <Chip size="small" color="error" label="Required" />}
                  </Stack>

                  {param.desc && (
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                      {param.desc}
                    </Typography>
                  )}

                  <Stack spacing={1.2}>
                    <TextField
                      label="Default Value"
                      value={form.defaultValue}
                      required={param.requiredYn === 'Y'}
                      type={numericType ? 'number' : 'text'}
                      onChange={(event) => handleFieldChange(param.paramCd, 'defaultValue', event.target.value)}
                      error={fieldError !== null}
                      helperText={fieldError ?? rangeText}
                      fullWidth
                    />

                    <Box
                      sx={{
                        display: 'grid',
                        gap: 1,
                        gridTemplateColumns: {
                          xs: '1fr',
                          sm: 'repeat(2, minmax(0, 1fr))',
                        },
                      }}
                    >
                      <TextField
                        label="Min Value"
                        value={form.minValue}
                        type="number"
                        onChange={(event) => handleFieldChange(param.paramCd, 'minValue', event.target.value)}
                        disabled={!numericType}
                        fullWidth
                      />
                      <TextField
                        label="Max Value"
                        value={form.maxValue}
                        type="number"
                        onChange={(event) => handleFieldChange(param.paramCd, 'maxValue', event.target.value)}
                        disabled={!numericType}
                        fullWidth
                      />
                      <TextField
                        label="UI Type"
                        value={form.uiType}
                        onChange={(event) => handleFieldChange(param.paramCd, 'uiType', event.target.value)}
                        fullWidth
                      />
                      <TextField
                        label="Step"
                        value={form.step}
                        type="number"
                        onChange={(event) => handleFieldChange(param.paramCd, 'step', event.target.value)}
                        disabled={!numericType}
                        fullWidth
                      />
                    </Box>
                  </Stack>
                </Box>
              );
            })}
          </Stack>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={saving}>
          Close
        </Button>
        <Button variant="contained" onClick={handleSave} disabled={!canSave}>
          {saving ? 'Saving...' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
