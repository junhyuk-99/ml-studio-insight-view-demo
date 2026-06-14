# ML Studio / Insight View Demo

Synthetic manufacturing AI analysis platform demo built with a Spring Boot backend, MongoDB sample dataset, FastAPI model execution server, and React frontend.

This repository is a public, rebuilt demo project. It is not a copy of production source code and does not include production data, customer data, real database connections, production equipment history, server IP addresses, private credentials, logs, certificates, or private environment values.

All data in this repository is synthetic sample data.

## Production & Learning Background

This public demo was rebuilt from experience gained during a deployed manufacturing AI analytics project and related manufacturing AI modeling study.

The original project focused on building an AI-assisted manufacturing data analysis platform for process data exploration, preprocessing, feature generation, anomaly detection, threshold alerts, supervised learning result review, and AI operation monitoring.

During the project, the main technical focus was not only displaying collected manufacturing data, but also designing an analysis pipeline that could transform raw process records into AI-ready features and interpretable result screens.

The work was developed while studying practical manufacturing AI modeling topics such as:

* Manufacturing process data structure analysis
* Raw data filtering and preprocessing
* Feature engineering for time-windowed process data
* Unsupervised anomaly detection
* Supervised classification result interpretation
* Threshold-based alert design
* AI model operation monitoring
* Manufacturing data visualization for field users

This repository is not a one-to-one copy of the production system. It preserves the main engineering concepts while replacing production-specific implementation details with synthetic data, simplified local runtime components, demo-safe APIs, and public portfolio screens.

Production source code, production screenshots, customer-specific information, server addresses, credentials, private Git history, and real equipment data are intentionally excluded.

## Demo Scope

The demo shows a synthetic manufacturing AI workflow using a local-only stack:

* Manufacturing dataset selection
* Raw process data exploration
* Time-series trend visualization
* Preprocessing and feature preview
* Feature engineering workflow
* Algorithm selection
* Model run policy review
* Anomaly detection result review
* Threshold alert review
* Supervised learning result summary
* AI operation overview dashboard

The demo is designed to explain how a manufacturing AI analysis platform can be structured from raw data to model result visualization.

It is not intended to provide production-grade model accuracy, production scheduling, real equipment integration, or customer-specific process logic.

## AI Pipeline

The main demo pipeline is:

```text
Synthetic Manufacturing Raw Data
        ↓
MongoDB Demo Dataset
        ↓
Data Exploration
        ↓
Preprocessing / Filtering
        ↓
Feature Engineering
        ↓
Algorithm Selection
        ↓
Model Run / Execution Policy
        ↓
Anomaly Detection Result
        ↓
Threshold Alert / Supervised Result Review
        ↓
AI Operation Dashboard
```

The public demo focuses on making this pipeline visible through screens and API responses.

### Pipeline Stages

| Stage               | Description                                                       |
| ------------------- | ----------------------------------------------------------------- |
| Raw Data            | Synthetic manufacturing process records stored in MongoDB         |
| Data Exploration    | Time-series chart and field-level process trend review            |
| Preprocessing       | Dataset, equipment, column, and preview-based filtering workflow  |
| Feature Engineering | Synthetic window-based feature preview and feature dataset review |
| Algorithm Selection | Demo policy for Isolation Forest, AutoEncoder, and Random Forest  |
| Model Run           | Synthetic model run records and active policy summary             |
| Anomaly Detection   | Anomaly score, status distribution, and result table              |
| Threshold Alert     | Demo threshold alert summary and alert list                       |
| Supervised Result   | Synthetic classification metrics and prediction distribution      |
| AI Overview         | Active model, recent run, signal highlight, and result summary    |

## Tech Stack

* Spring Boot API
* FastAPI AI execution server
* Vite + React + TypeScript frontend
* MongoDB
* Python seed script
* Docker Compose
* Recharts / chart-based dashboard components

## Screenshots

### AI Operation Overview

![AI Operation Overview](screenshots/ai-overview.png)

### Preprocess / Feature Engineering

![Preprocess Feature Engineering](screenshots/preprocess-feature-engineering.png)

### Algorithm Selection

![Algorithm Selection](screenshots/algorithm-selection.png)

### Model Training / Run Policy

![Model Training Run Policy](screenshots/model-training-run-policy.png)

### Anomaly Detection Result

![Anomaly Detection Result](screenshots/anomaly-detection-result.png)

### Time-Series Data Exploration

![Time-Series Data Exploration](screenshots/timeseries-data-exploration.png)

## Architecture

```text
Synthetic Sample Data
        ↓
MongoDB
        ↓
Spring Boot Demo API
        ↓
FastAPI Demo AI Server
        ↓
React ML Studio / Insight View Dashboard
```

The production project followed the same general concept, but used operational manufacturing data, internal configuration, deployment-specific infrastructure, and production security boundaries that are not included in this repository.

## Local Demo Flow

1. Start local MongoDB.
2. Load synthetic demo seed data.
3. Run the FastAPI AI server.
4. Run the Spring Boot backend API.
5. Run the React frontend.
6. Open the dashboard and review the synthetic AI workflow.

## Sample Data

Load local synthetic sample data with:

```powershell
python scripts\seed-demo-data.py --uri mongodb://localhost:27017 --db ml_studio_demo
```

The generated records are fake demo records only. They are not copied from real production systems.

Main synthetic collections include:

* `THISHMIDATA`
* `TMSTMC`
* `tmst_dataset_config`
* `tmst_feature_mst`
* `thisfeature`
* `tmst_algo_mst`
* `tmst_algo_dtl`
* `tmst_model_policy`
* `tmst_model_active`
* `thismodelrun`
* `thisanomalyresult`
* `thisthresholdalert`
* `thisclassificationresult`
* `thismodeleval`

The canonical public demo dataset key is:

```text
DEMO_DATASET_MANUFACTURING_AI
```

## Backend API

The Spring Boot backend lives in `apps/api/` and exposes demo-safe APIs over synthetic MongoDB collections.

Configuration defaults:

* Java 17
* Spring Boot 3.x
* Server port `8090`
* MongoDB URI `${MONGODB_URI:mongodb://localhost:27017/ml_studio_demo}`
* CORS origin `http://localhost:5173`

Run:

```powershell
cd apps\api
.\gradlew.bat bootRun
```

Build:

```powershell
cd apps\api
.\gradlew.bat build -x test
```

Representative API areas:

* `/api/health`
* `/api/home/dashboard`
* `/api/data-exploration/datasets`
* `/api/data-exploration/timeseries/*`
* `/api/preprocess/*`
* `/api/algorithms/selection`
* `/api/modeltrain/*`
* `/api/threshold-alert/*`
* `/api/supervised/result/*`

## AI Server

The FastAPI AI server lives in `apps/ai-server/`.

It provides demo-safe model execution endpoints for the synthetic pipeline:

* Isolation Forest
* AutoEncoder
* Random Forest

Run:

```powershell
cd apps\ai-server
python -m uvicorn main:app --host 0.0.0.0 --port 8001
```

Compile check:

```powershell
cd apps\ai-server
python -m compileall .
```

The AI server is used for local demonstration only. It does not include production model files, production training data, or customer-specific model parameters.

## Frontend

The React frontend lives in `apps/web/`.

Run:

```powershell
cd apps\web
npm install
npm run dev
```

Open:

```text
http://localhost:5173
```

Build:

```powershell
cd apps\web
npm run build
```

The public demo includes a demo-safe login flow for portfolio review. It does not provide production authentication, customer accounts, user administration, or real authorization logic.

## Local Runtime

Run the full local demo:

```powershell
docker compose up -d mongo
```

Load seed data:

```powershell
python scripts\seed-demo-data.py --uri mongodb://localhost:27017 --db ml_studio_demo
```

Start the AI server:

```powershell
cd apps\ai-server
python -m uvicorn main:app --host 0.0.0.0 --port 8001
```

Start the backend API:

```powershell
cd apps\api
.\gradlew.bat bootRun
```

Start the frontend:

```powershell
cd apps\web
npm install
npm run dev
```

Open:

```text
http://localhost:5173
```

## Learning / Study Notes

This project also documents a practical learning process around manufacturing AI.

The key study areas were:

* How to identify meaningful manufacturing variables from raw process data
* How to filter process records by dataset, equipment, time range, and sensor field
* How to convert raw records into feature-ready structures
* How to compare unsupervised anomaly detection and supervised classification workflows
* How to present AI outputs as field-readable dashboards rather than raw model results
* How to separate public demo data from private production data
* How to design a portfolio-safe synthetic version of a deployed AI platform

The purpose of this repository is to show both implementation and learning progression: understanding manufacturing data, building an AI analysis workflow, and presenting the results through a web-based system.

## Security Notice

* Do not add production `.env` files.
* Do not add real DB URIs, server IPs, credentials, keys, certificates, logs, dumps, or customer screenshots.
* Do not import private repository history.
* Do not include production source code or production Git history.
* Do not include real process data, real equipment identifiers, or real customer information.
* Use only synthetic data in `demo-data/seed/`.

## Documentation

| Document        | Description                                             |
| --------------- | ------------------------------------------------------- |
| Architecture    | System architecture and data flow overview              |
| API Reference   | Backend API endpoints and response format               |
| Data Schema     | MongoDB demo schema and collection structure            |
| Security Notice | Security, anonymization, and disclosure policy          |
| Data Notice     | Synthetic data and data handling notice                 |
| Case Study      | Anonymized ML Studio / Insight View case study          |
| Learning Notes  | Manufacturing AI preprocessing and modeling study notes |
