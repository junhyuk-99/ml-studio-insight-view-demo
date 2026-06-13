from __future__ import annotations

from dataclasses import dataclass
from math import isfinite

import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score, precision_score, recall_score
from sklearn.model_selection import train_test_split

from app.schemas import (
    RandomForestExecuteRequest,
    RandomForestExecuteResponse,
    RandomForestFeatureImportanceRow,
    RandomForestModelParams,
    RandomForestPredictionRow,
)


@dataclass(frozen=True)
class _ResolvedParams:
    n_estimators: int
    max_depth: int | None
    min_samples_split: int
    min_samples_leaf: int
    max_features: str
    class_weight: str
    train_valid_ratio: float
    seed: int


@dataclass(frozen=True)
class _RowMeta:
    source_index: int | None
    labeled_doc_id: str | None
    source_id: str | None
    actual_label: int


def run_random_forest(request: RandomForestExecuteRequest) -> RandomForestExecuteResponse:
    request_rows = request.rows if request.rows else request.records
    if not request_rows:
        raise ValueError("rows or records must not be empty.")

    feature_columns = _resolve_feature_columns(request_rows, request.feature_columns)
    label_field = _normalize_text(request.label_field) or "label"

    row_metas: list[_RowMeta] = []
    matrix_rows: list[list[float]] = []
    labels: list[int] = []
    excluded_unknown_count = 0

    for row_index, row in enumerate(request_rows):
        label = _resolve_label(row, label_field)
        if label == 9:
            excluded_unknown_count += 1
            continue
        if label not in (0, 1):
            continue

        matrix_rows.append(_build_matrix_row(row.input_features, feature_columns))
        labels.append(label)
        row_metas.append(
            _RowMeta(
                source_index=row.source_index if row.source_index is not None else row_index,
                labeled_doc_id=_normalize_text(row.labeled_doc_id),
                source_id=_normalize_text(row.source_id),
                actual_label=label,
            )
        )

    if not matrix_rows:
        raise ValueError("No trainable rows found after filtering labels. expected labels in [0, 1].")

    unique_labels = set(labels)
    if len(unique_labels) < 2:
        raise ValueError("Random Forest requires both label 0 and label 1 in training data.")
    if len(matrix_rows) < 2:
        raise ValueError("At least two rows are required for train/test split.")

    params = _resolve_params(request.params)
    x = np.asarray(matrix_rows, dtype=np.float64)
    y = np.asarray(labels, dtype=np.int64)
    indices = np.arange(len(matrix_rows))

    test_size = max(0.05, min(0.95, 1.0 - params.train_valid_ratio))
    stratify_labels = y if _can_stratify(y, test_size) else None

    train_idx, test_idx = train_test_split(
        indices,
        test_size=test_size,
        random_state=params.seed,
        shuffle=True,
        stratify=stratify_labels,
    )
    if train_idx.size == 0 or test_idx.size == 0:
        raise ValueError("Failed to create non-empty train/test split.")

    x_train = x[train_idx]
    y_train = y[train_idx]
    x_test = x[test_idx]
    y_test = y[test_idx]

    model = RandomForestClassifier(
        n_estimators=params.n_estimators,
        max_depth=params.max_depth,
        min_samples_split=params.min_samples_split,
        min_samples_leaf=params.min_samples_leaf,
        max_features=params.max_features,
        class_weight=params.class_weight,
        random_state=params.seed,
        n_jobs=-1,
    )
    model.fit(x_train, y_train)

    class_index = {int(label): idx for idx, label in enumerate(model.classes_)}
    if 0 not in class_index or 1 not in class_index:
        raise ValueError("Model classes must include both 0 and 1.")

    y_pred_train = model.predict(x_train).astype(np.int64)
    y_pred_test = model.predict(x_test).astype(np.int64)
    proba_train = model.predict_proba(x_train)
    proba_test = model.predict_proba(x_test)

    tn, fp, fn, tp = confusion_matrix(y_test, y_pred_test, labels=[0, 1]).ravel()
    accuracy = float(accuracy_score(y_test, y_pred_test))
    precision = float(precision_score(y_test, y_pred_test, zero_division=0))
    recall = float(recall_score(y_test, y_pred_test, zero_division=0))
    f1 = float(f1_score(y_test, y_pred_test, zero_division=0))

    predictions: list[RandomForestPredictionRow] = []
    predictions.extend(
        _build_predictions(
            index_array=train_idx,
            y_pred=y_pred_train,
            probabilities=proba_train,
            class_index=class_index,
            row_metas=row_metas,
            split_type="TRAIN",
        )
    )
    predictions.extend(
        _build_predictions(
            index_array=test_idx,
            y_pred=y_pred_test,
            probabilities=proba_test,
            class_index=class_index,
            row_metas=row_metas,
            split_type="TEST",
        )
    )
    feature_importances = _extract_feature_importances(model, feature_columns)

    return RandomForestExecuteResponse(
        run_id=_normalize_text(request.run_id),
        dataset_key=_normalize_text(request.dataset_key),
        algo_code=_normalize_text(request.algo_code) or "RANDOM_FOREST",
        accuracy=accuracy,
        precision=precision,
        recall=recall,
        f1_score=f1,
        tp=int(tp),
        tn=int(tn),
        fp=int(fp),
        fn=int(fn),
        train_count=int(train_idx.size),
        test_count=int(test_idx.size),
        total_count=int(len(matrix_rows)),
        excluded_unknown_count=int(excluded_unknown_count),
        predictions=predictions,
        feature_importances=feature_importances,
    )


def _build_predictions(
    index_array: np.ndarray,
    y_pred: np.ndarray,
    probabilities: np.ndarray,
    class_index: dict[int, int],
    row_metas: list[_RowMeta],
    split_type: str,
) -> list[RandomForestPredictionRow]:
    rows: list[RandomForestPredictionRow] = []
    for local_index, original_index in enumerate(index_array.tolist()):
        meta = row_metas[original_index]
        predicted = int(y_pred[local_index])
        prob_normal = float(probabilities[local_index][class_index[0]])
        prob_anomaly = float(probabilities[local_index][class_index[1]])
        prob_predicted = prob_anomaly if predicted == 1 else prob_normal
        rows.append(
            RandomForestPredictionRow(
                source_index=meta.source_index,
                labeled_doc_id=meta.labeled_doc_id,
                source_id=meta.source_id,
                actual_label=meta.actual_label,
                prediction_label=predicted,
                prediction_probability=prob_predicted,
                prediction_probability_normal=prob_normal,
                prediction_probability_anomaly=prob_anomaly,
                split_type=split_type,
                error_type=_resolve_error_type(meta.actual_label, predicted),
            )
        )
    return rows


def _extract_feature_importances(
    model: RandomForestClassifier,
    feature_columns: list[str],
) -> list[RandomForestFeatureImportanceRow]:
    if not feature_columns:
        return []

    try:
        raw_importances = getattr(model, "feature_importances_", None)
    except Exception:
        return []
    if raw_importances is None:
        return []

    try:
        importances = np.asarray(raw_importances, dtype=np.float64).reshape(-1)
    except Exception:
        return []
    if importances.size == 0:
        return []

    safe_length = min(len(feature_columns), int(importances.size))
    if safe_length <= 0:
        return []

    rows: list[tuple[str, float]] = []
    for index in range(safe_length):
        feature_name = _normalize_text(feature_columns[index])
        if feature_name is None:
            continue
        importance = float(importances[index])
        if not isfinite(importance):
            importance = 0.0
        rows.append((feature_name, importance))

    rows.sort(key=lambda item: item[1], reverse=True)
    return [
        RandomForestFeatureImportanceRow(
            rank=rank,
            feature=feature_name,
            importance=importance,
        )
        for rank, (feature_name, importance) in enumerate(rows, start=1)
    ]


def _resolve_feature_columns(rows, requested_columns: list[str]) -> list[str]:
    if requested_columns:
        normalized = [_normalize_text(column) for column in requested_columns]
        filtered = [column for column in normalized if column]
        if filtered:
            return list(dict.fromkeys(filtered))

    first = rows[0].input_features
    if not first:
        raise ValueError("input_features must not be empty.")
    feature_columns = [_normalize_text(name) for name in first.keys()]
    feature_columns = [name for name in feature_columns if name]
    if not feature_columns:
        raise ValueError("input_features must not be empty.")
    return list(dict.fromkeys(feature_columns))


def _resolve_label(row, label_field: str) -> int | None:
    if row.label is not None:
        return _to_int(row.label)

    extra = getattr(row, "__pydantic_extra__", None) or {}
    return _to_int(extra.get(label_field))


def _build_matrix_row(input_features: dict[str, object], feature_columns: list[str]) -> list[float]:
    values: list[float] = []
    for feature_name in feature_columns:
        values.append(_safe_to_float(input_features.get(feature_name)))
    return values


def _resolve_params(params: RandomForestModelParams | None) -> _ResolvedParams:
    resolved = params or RandomForestModelParams()
    n_estimators = max(1, int(resolved.n_estimators if resolved.n_estimators is not None else 250))

    max_depth: int | None
    if resolved.max_depth is None:
        max_depth = 10
    else:
        numeric_max_depth = int(resolved.max_depth)
        max_depth = None if numeric_max_depth <= 0 else numeric_max_depth

    min_samples_split = max(2, int(resolved.min_samples_split if resolved.min_samples_split is not None else 2))
    min_samples_leaf = max(1, int(resolved.min_samples_leaf if resolved.min_samples_leaf is not None else 1))
    max_features = (_normalize_text(resolved.max_features) or "sqrt").lower()
    class_weight = _normalize_text(resolved.class_weight) or "balanced"
    train_valid_ratio = resolved.train_valid_ratio if resolved.train_valid_ratio is not None else 0.8
    train_valid_ratio = max(0.05, min(0.95, float(train_valid_ratio)))
    seed = int(resolved.seed if resolved.seed is not None else 42)

    return _ResolvedParams(
        n_estimators=n_estimators,
        max_depth=max_depth,
        min_samples_split=min_samples_split,
        min_samples_leaf=min_samples_leaf,
        max_features=max_features,
        class_weight=class_weight,
        train_valid_ratio=train_valid_ratio,
        seed=seed,
    )


def _can_stratify(y: np.ndarray, test_size: float) -> bool:
    if y.size < 4:
        return False
    labels, counts = np.unique(y, return_counts=True)
    if labels.size < 2:
        return False
    if np.min(counts) < 2:
        return False
    estimated_test = int(round(y.size * test_size))
    return estimated_test >= labels.size


def _resolve_error_type(actual_label: int, predicted_label: int) -> str:
    if actual_label == 1 and predicted_label == 1:
        return "TP"
    if actual_label == 0 and predicted_label == 0:
        return "TN"
    if actual_label == 0 and predicted_label == 1:
        return "FP"
    return "FN"


def _safe_to_float(value: object) -> float:
    if value is None:
        return 0.0
    if isinstance(value, bool):
        return 1.0 if value else 0.0
    try:
        numeric = float(value)
    except (TypeError, ValueError):
        return 0.0
    if not isfinite(numeric):
        return 0.0
    return numeric


def _to_int(value: object) -> int | None:
    if value is None:
        return None
    if isinstance(value, bool):
        return 1 if value else 0
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        if not isfinite(value):
            return None
        return int(value)
    if isinstance(value, str):
        stripped = value.strip()
        if not stripped:
            return None
        try:
            return int(float(stripped))
        except ValueError:
            return None
    return None


def _normalize_text(value: object) -> str | None:
    if value is None:
        return None
    normalized = str(value).strip()
    return normalized if normalized else None
