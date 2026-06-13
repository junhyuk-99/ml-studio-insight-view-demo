# Architecture

The demo is split into three applications and a MongoDB data store.

- `apps/web`: React dashboard for monitoring, dataset setup, model execution, result analysis, and data exploration.
- `apps/api`: Spring Boot API that exposes dashboard endpoints, resolves demo collection schemas, coordinates model execution, and reads/writes MongoDB collections.
- `apps/ai-server`: FastAPI service for model execution workflows such as anomaly detection and supervised learning.
- MongoDB: local demo database named `demo_ml_studio_db`.

## Data Flow

1. Synthetic telemetry is loaded into MongoDB seed collections.
2. The Spring Boot API exposes equipment, dataset, preprocessing, model, result, alert, and dashboard data.
3. The React dashboard calls the API through `VITE_API_BASE_URL`.
4. Model execution requests are forwarded from Spring Boot to the FastAPI AI server.
5. AI outputs are represented as model result collections and read back by the dashboard.

## AI Analysis Flow

1. Select a demo dataset such as `DEMO_DATASET_001`.
2. Generate or read feature windows from synthetic HMI records.
3. Execute a model run such as `DEMO_RUN_001`.
4. Store anomaly, classification, threshold, and feature importance outputs.
5. Review status, metrics, explanations, and trends in the dashboard.
