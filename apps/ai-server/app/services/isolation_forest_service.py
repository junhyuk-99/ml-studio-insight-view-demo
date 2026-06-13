from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from sklearn.ensemble import IsolationForest

from app.schemas import InferenceRow, ModelParams


@dataclass(frozen=True)
class IsolationForestOutputRow:
    window_start: object
    window_end: object
    anomaly_score: float
    is_anomaly: bool
    health_index: float
    status: str


def run_isolation_forest(rows: list[InferenceRow], model_params: ModelParams | None) -> list[IsolationForestOutputRow]:
    if not rows:
        raise ValueError("rows must not be empty.")

    feature_names = _resolve_feature_names(rows)
    feature_matrix = _build_feature_matrix(rows, feature_names)
    clf = _build_model(model_params)

    clf.fit(feature_matrix)
    raw_scores = clf.score_samples(feature_matrix)
    predictions = clf.predict(feature_matrix)

    raw_anomaly_scores = -raw_scores
    min_score = float(np.min(raw_anomaly_scores))
    max_score = float(np.max(raw_anomaly_scores))
    score_range = max_score - min_score

    outputs: list[IsolationForestOutputRow] = []
    for index, row in enumerate(rows):
        anomaly_score = float(raw_anomaly_scores[index])
        is_anomaly = bool(predictions[index] == -1)

        if score_range <= 1e-12:
            normalized_score = 0.0
        else:
            normalized_score = float((anomaly_score - min_score) / score_range)

        operational_risk = normalized_score * 0.35

        if is_anomaly:
            operational_risk = max(operational_risk, 0.45)

        health_index = max(0.0, min(1.0, 1.0 - operational_risk))
        outputs.append(
            IsolationForestOutputRow(
                window_start=row.window_start,
                window_end=row.window_end,
                anomaly_score=anomaly_score,
                is_anomaly=is_anomaly,
                health_index=health_index,
                status=_resolve_status(health_index, is_anomaly),
            )
        )
    return outputs


def _resolve_feature_names(rows: list[InferenceRow]) -> list[str]:
    first_features = rows[0].input_features
    if not first_features:
        raise ValueError("rows[0].input_features must not be empty.")

    first_key_order = list(first_features.keys())
    if not first_key_order:
        raise ValueError("rows[0].input_features must not be empty.")

    for index, row in enumerate(rows[1:], start=1):
        if list(row.input_features.keys()) != first_key_order:
            raise RuntimeError(f"input_features keys mismatch at row index {index}.")

    return first_key_order


def _build_feature_matrix(rows: list[InferenceRow], feature_names: list[str]) -> np.ndarray:
    matrix_rows: list[list[float]] = []
    for row_index, row in enumerate(rows):
        values: list[float] = []
        for feature_name in feature_names:
            raw_value = row.input_features.get(feature_name)
            try:
                numeric_value = float(raw_value)
            except (TypeError, ValueError) as exc:
                raise RuntimeError(
                    f"input_features value must be numeric. row={row_index}, feature={feature_name}"
                ) from exc
            values.append(numeric_value)
        matrix_rows.append(values)
    return np.asarray(matrix_rows, dtype=np.float64)


def _build_model(model_params: ModelParams | None) -> IsolationForest:
    params = model_params or ModelParams()

    contamination = "auto" if params.contamination is None else params.contamination
    n_estimators = 100 if params.n_estimators is None else int(params.n_estimators)
    max_samples = "auto" if params.max_samples is None else params.max_samples
    random_state = 42 if params.random_state is None else int(params.random_state)

    if isinstance(contamination, str):
        contamination_value: float | str = contamination.strip() or "auto"
    else:
        contamination_value = float(contamination)

    if isinstance(max_samples, str):
        stripped = max_samples.strip()
        if stripped == "":
            max_samples_value: float | int | str = "auto"
        else:
            try:
                parsed = float(stripped)
                if parsed.is_integer() and parsed >= 1:
                    max_samples_value = int(parsed)
                else:
                    max_samples_value = parsed
            except ValueError:
                max_samples_value = stripped
    elif isinstance(max_samples, int):
        max_samples_value = max_samples
    else:
        max_samples_value = float(max_samples)

    return IsolationForest(
        contamination=contamination_value,
        n_estimators=n_estimators,
        max_samples=max_samples_value,
        random_state=random_state,
    )


def _resolve_status(health_index: float, is_anomaly: bool) -> str:
    if health_index < 0.60:
        return "CRITICAL"
    if is_anomaly or health_index < 0.80:
        return "WARNING"
    return "NORMAL"
