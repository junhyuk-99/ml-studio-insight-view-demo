from __future__ import annotations

from dataclasses import dataclass
import warnings

import numpy as np
from sklearn.exceptions import ConvergenceWarning
from sklearn.neural_network import MLPRegressor
from sklearn.preprocessing import StandardScaler

from app.schemas import AutoEncoderModelParams, InferenceRow


@dataclass(frozen=True)
class AutoEncoderOutputRow:
    window_start: object
    window_end: object
    anomaly_score: float
    is_anomaly: bool
    health_index: float
    status: str


def run_autoencoder(
    rows: list[InferenceRow], model_params: AutoEncoderModelParams | None
) -> tuple[list[AutoEncoderOutputRow], dict[str, object]]:
    if not rows:
        raise ValueError("rows must not be empty.")

    feature_names = _resolve_feature_names(rows)
    feature_matrix = _build_feature_matrix(rows, feature_names)
    scaler = StandardScaler()
    scaled_matrix = scaler.fit_transform(feature_matrix)

    params = model_params or AutoEncoderModelParams()
    hidden_units = _resolve_positive_int(params.hidden_units, default=max(16, min(128, feature_matrix.shape[1] * 2)))
    latent_dim = _resolve_positive_int(params.latent_dim, default=max(2, min(hidden_units - 1, max(2, feature_matrix.shape[1] // 2))))
    if latent_dim >= hidden_units:
        latent_dim = max(1, hidden_units - 1)

    batch_size = _resolve_positive_int(params.batch_size, default=min(64, max(8, feature_matrix.shape[0])))
    epoch = _resolve_positive_int(params.epoch, default=120)
    learning_rate = _resolve_positive_float(params.learning_rate, default=0.001)
    train_valid_ratio = _resolve_ratio(params.train_valid_ratio, default=0.8)
    early_stopping = True if params.early_stopping is None else bool(params.early_stopping)
    patience = _resolve_positive_int(params.patience, default=10)
    contamination = _resolve_contamination(params.contamination)
    seed = 42 if params.seed is None else int(params.seed)

    if scaled_matrix.shape[0] < 20:
        # Sparse row counts make validation split unstable; disable early stopping for deterministic training.
        early_stopping = False

    validation_fraction = max(0.05, min(0.4, 1.0 - train_valid_ratio))
    effective_train_rows = scaled_matrix.shape[0]
    if early_stopping:
        effective_train_rows = max(1, int(round(scaled_matrix.shape[0] * (1.0 - validation_fraction))))
    effective_batch_size = max(1, min(batch_size, effective_train_rows))

    model = MLPRegressor(
        hidden_layer_sizes=(hidden_units, latent_dim, hidden_units),
        activation="relu",
        solver="adam",
        learning_rate_init=learning_rate,
        batch_size=effective_batch_size,
        max_iter=epoch,
        shuffle=True,
        random_state=seed,
        early_stopping=early_stopping,
        validation_fraction=validation_fraction,
        n_iter_no_change=patience,
    )

    with warnings.catch_warnings():
        warnings.filterwarnings("ignore", category=ConvergenceWarning)
        model.fit(scaled_matrix, scaled_matrix)

    reconstructed = model.predict(scaled_matrix)
    reconstruction_errors = np.mean(np.square(scaled_matrix - reconstructed), axis=1)

    quantile = max(0.0, min(1.0, 1.0 - contamination))
    threshold = float(np.quantile(reconstruction_errors, quantile))
    min_error = float(np.min(reconstruction_errors))
    max_error = float(np.max(reconstruction_errors))
    denominator = max(max_error - min_error, 1e-12)
    normalized_scores = (reconstruction_errors - min_error) / denominator

    outputs: list[AutoEncoderOutputRow] = []
    for index, row in enumerate(rows):
        anomaly_score = float(normalized_scores[index])
        is_anomaly = bool(reconstruction_errors[index] >= threshold)
        health_index = float(max(0.0, min(1.0, 1.0 - anomaly_score)))
        outputs.append(
            AutoEncoderOutputRow(
                window_start=row.window_start,
                window_end=row.window_end,
                anomaly_score=anomaly_score,
                is_anomaly=is_anomaly,
                health_index=health_index,
                status=_resolve_status(anomaly_score, is_anomaly),
            )
        )

    reserved_params: list[str] = []
    if params.sequence_length is not None:
        reserved_params.append("sequence_length")
    if params.dropout is not None:
        reserved_params.append("dropout")

    meta: dict[str, object] = {
        "input_mode": "DENSE_ROW_WISE",
        "reserved_params": reserved_params,
        "effective_early_stopping": early_stopping,
        "feature_count": int(feature_matrix.shape[1]),
        "row_count": int(feature_matrix.shape[0]),
        "threshold": threshold,
        "contamination": contamination,
    }
    if params.sequence_length is not None:
        meta["sequence_length_mode"] = "RESERVED_NOT_USED_IN_DENSE_V1"
    if params.dropout is not None:
        meta["dropout_mode"] = "RESERVED_NOT_USED_IN_DENSE_V1"

    return outputs, meta


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


def _resolve_positive_int(value: int | None, default: int) -> int:
    if value is None:
        return default
    return max(1, int(value))


def _resolve_positive_float(value: float | None, default: float) -> float:
    if value is None:
        return default
    return max(1e-8, float(value))


def _resolve_ratio(value: float | None, default: float) -> float:
    if value is None:
        return default
    numeric = float(value)
    if numeric <= 0.0:
        return 0.01
    if numeric >= 1.0:
        return 0.99
    return numeric


def _resolve_contamination(value: float | str | None) -> float:
    if value is None:
        return 0.05
    if isinstance(value, str):
        stripped = value.strip()
        if stripped == "":
            return 0.05
        if stripped.lower() == "auto":
            return 0.05
        numeric = float(stripped)
    else:
        numeric = float(value)
    return max(1e-6, min(0.5, numeric))


def _resolve_status(anomaly_score: float, is_anomaly: bool) -> str:
    if not is_anomaly and anomaly_score < 0.5:
        return "NORMAL"
    if anomaly_score >= 0.8:
        return "CRITICAL"
    return "WARNING"
