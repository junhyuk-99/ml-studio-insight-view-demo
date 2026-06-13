from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ModelParams(BaseModel):
    contamination: float | str | None = None
    n_estimators: int | None = None
    max_samples: float | int | str | None = None
    random_state: int | None = None

    model_config = ConfigDict(extra="ignore")


class AutoEncoderModelParams(BaseModel):
    sequence_length: int | None = None
    hidden_units: int | None = None
    latent_dim: int | None = None
    batch_size: int | None = None
    epoch: int | None = None
    dropout: float | None = None
    learning_rate: float | None = None
    train_valid_ratio: float | None = None
    early_stopping: bool | None = None
    patience: int | None = None
    contamination: float | str | None = None
    seed: int | None = None

    model_config = ConfigDict(extra="ignore")


class RandomForestModelParams(BaseModel):
    n_estimators: int | None = None
    max_depth: int | None = None
    min_samples_split: int | None = None
    min_samples_leaf: int | None = None
    max_features: str | None = None
    class_weight: str | None = None
    train_valid_ratio: float | None = None
    seed: int | None = None

    model_config = ConfigDict(extra="ignore")


class ExecutionMeta(BaseModel):
    run_id: str | None = None
    equipment_id: str | None = None
    sensor_id: str | None = None
    dataset_key: str | dict[str, str] | None = None
    meta_only_param_keys: list[str] = Field(default_factory=list)

    model_config = ConfigDict(extra="ignore")


class InferenceRow(BaseModel):
    window_start: Any
    window_end: Any
    input_features: dict[str, Any]

    model_config = ConfigDict(extra="ignore")


class IsolationForestExecuteRequest(BaseModel):
    algorithm: str
    model_params: ModelParams | None = None
    execution_meta: ExecutionMeta | None = None
    rows: list[InferenceRow]

    model_config = ConfigDict(extra="ignore")


class IsolationForestResultRow(BaseModel):
    window_start: Any
    window_end: Any
    anomaly_score: float
    is_anomaly: bool
    health_index: float | None = None
    status: str | None = None


class IsolationForestExecuteResponse(BaseModel):
    ok: bool = True
    results: list[IsolationForestResultRow]


class AutoEncoderExecuteRequest(BaseModel):
    algorithm: str
    model_params: AutoEncoderModelParams | None = None
    execution_meta: ExecutionMeta | None = None
    rows: list[InferenceRow]

    model_config = ConfigDict(extra="ignore")


class AutoEncoderResultRow(BaseModel):
    window_start: Any
    window_end: Any
    anomaly_score: float
    is_anomaly: bool
    health_index: float
    status: str


class AutoEncoderExecuteResponse(BaseModel):
    ok: bool = True
    results: list[AutoEncoderResultRow]
    meta: dict[str, Any] = Field(default_factory=dict)


class RandomForestInputRow(BaseModel):
    source_index: int | None = None
    labeled_doc_id: str | None = None
    source_id: str | None = None
    label: int | None = None
    input_features: dict[str, Any]

    model_config = ConfigDict(extra="allow")


class RandomForestExecuteRequest(BaseModel):
    run_id: str | None = None
    dataset_key: str | None = None
    algo_code: str | None = None
    feature_columns: list[str] = Field(default_factory=list)
    rows: list[RandomForestInputRow] = Field(default_factory=list)
    records: list[RandomForestInputRow] = Field(default_factory=list)
    params: RandomForestModelParams | None = None
    label_field: str | None = "label"

    model_config = ConfigDict(extra="ignore")


class RandomForestPredictionRow(BaseModel):
    source_index: int | None = None
    labeled_doc_id: str | None = None
    source_id: str | None = None
    actual_label: int
    prediction_label: int
    prediction_probability: float
    prediction_probability_normal: float
    prediction_probability_anomaly: float
    split_type: str
    error_type: str


class RandomForestFeatureImportanceRow(BaseModel):
    rank: int
    feature: str
    importance: float


class RandomForestExecuteResponse(BaseModel):
    run_id: str | None = None
    dataset_key: str | None = None
    algo_code: str = "RANDOM_FOREST"
    accuracy: float
    precision: float
    recall: float
    f1_score: float
    tp: int
    tn: int
    fp: int
    fn: int
    train_count: int
    test_count: int
    total_count: int
    excluded_unknown_count: int
    predictions: list[RandomForestPredictionRow] = Field(default_factory=list)
    feature_importances: list[RandomForestFeatureImportanceRow] = Field(default_factory=list)
