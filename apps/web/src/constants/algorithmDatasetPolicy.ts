const DEFAULT_DEMO_DATASET_KEY = 'DEMO_DATASET_MANUFACTURING_AI';
const DEFAULT_UNSUPERVISED_DATASET_KEY = DEFAULT_DEMO_DATASET_KEY;
const RANDOM_FOREST_SUPERVISED_DATASET_KEY = DEFAULT_DEMO_DATASET_KEY;

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

export function resolveDatasetKeyForAlgorithm(
  algoCode: string | null | undefined,
  fallbackDatasetKey: string | null | undefined = DEFAULT_UNSUPERVISED_DATASET_KEY,
): string {
  const normalizedAlgoCode = normalizeAlgoCode(algoCode);

  if (normalizedAlgoCode === 'RANDOM_FOREST') {
    return normalizeDatasetKey(fallbackDatasetKey) ?? DEFAULT_UNSUPERVISED_DATASET_KEY;
  }

  if (normalizedAlgoCode === 'ISOLATION_FOREST' || normalizedAlgoCode === 'AUTOENCODER') {
    return DEFAULT_UNSUPERVISED_DATASET_KEY;
  }

  return normalizeDatasetKey(fallbackDatasetKey) ?? DEFAULT_UNSUPERVISED_DATASET_KEY;
}

export function isRandomForestAlgorithm(algoCode: string | null | undefined): boolean {
  return normalizeAlgoCode(algoCode) === 'RANDOM_FOREST';
}

export function defaultAlgorithmDatasetKey(): string {
  return DEFAULT_UNSUPERVISED_DATASET_KEY;
}

export function randomForestDatasetKey(): string {
  return RANDOM_FOREST_SUPERVISED_DATASET_KEY;
}
