# Case Study: ML Studio / Insight View

## 1. Overview

This case study summarizes a manufacturing AI analytics project experience and the public synthetic demo rebuilt from it.

The original project focused on building an AI-assisted manufacturing data analysis platform that could support process data exploration, preprocessing, feature generation, anomaly detection, threshold monitoring, supervised learning result review, and AI operation monitoring.

The public repository is not the production system. It is a rebuilt portfolio demo that uses synthetic data and local-only runtime components to demonstrate the same engineering concepts without exposing production source code, production data, customer information, infrastructure details, or private configuration.

The demo is designed to show the full analysis workflow:

```text
Raw Manufacturing Data
→ Data Exploration
→ Preprocessing / Filtering
→ Feature Engineering
→ Algorithm Selection
→ Model Run
→ Anomaly Detection Result
→ Threshold / Supervised Result Review
→ AI Operation Dashboard
```

## 2. Background

Manufacturing AI projects require more than simply connecting charts to collected sensor data.

Raw process records often contain many variables, timestamps, equipment identifiers, status values, and operating conditions. Before applying AI models, the data must be understood, filtered, transformed, and organized into features that are meaningful for model execution and result interpretation.

The original ML Studio / Insight View project was developed to support this process through a web-based platform. It provided screens for data exploration, preprocessing, algorithm selection, model execution, anomaly result review, threshold alerts, supervised learning metrics, and AI operation status.

The public demo rebuilds this concept using synthetic manufacturing data and simplified local runtime services.

## 3. Problem

Manufacturing analytics teams often need to inspect telemetry, prepare model-ready datasets, execute AI models, and review results across disconnected tools.

This creates several issues:

* Raw process data is difficult to interpret directly.
* Sensor and process variables must be filtered before they can be used for AI analysis.
* Feature generation rules need to be visible and repeatable.
* Model execution status and result history must be traceable.
* Anomaly detection results must be translated into field-readable indicators.
* Threshold alerts and supervised learning metrics should be reviewed in the same operational context.
* Users need a dashboard-style interface rather than isolated scripts or raw model output.

The project addressed these issues by organizing the workflow into an integrated ML Studio / Insight View structure.

## 4. Development Challenges

### 4.1 Understanding manufacturing AI data

The first challenge was understanding what kind of manufacturing data should be used for AI analysis.

Raw process data usually contains many columns, but not every column is useful for anomaly detection or supervised learning. Some fields represent process values, some represent setpoints, some represent status codes, and others may be identifiers or metadata.

The project required reviewing available data fields and separating them into practical categories:

* Time fields
* Equipment identifiers
* Process variables
* Sensor values
* Status values
* Dataset metadata
* Feature candidates
* Model result fields

This helped define which fields should be exposed in the data exploration and preprocessing screens.

### 4.2 Designing preprocessing and filtering rules

The second challenge was designing a preprocessing flow that was understandable from the UI.

The preprocessing workflow needed to support basic decisions such as:

* Which dataset should be analyzed?
* Which equipment or equipment group should be included?
* Which time range should be used?
* Which numeric fields should be selected?
* Which records should be filtered out?
* Which columns should become feature inputs?

The public demo simplifies this into a synthetic workflow, but the main idea remains the same: raw records should not go directly into model result screens. They must first pass through a visible filtering and feature preparation process.

### 4.3 Building feature-ready datasets

The project used the concept of feature datasets to separate raw manufacturing records from model-ready inputs.

In the public demo, synthetic raw records are represented in `THISHMIDATA`, while feature preview and feature rows are represented in `thisfeature`.

This separation is important because AI models usually require structured input features rather than raw event records.

The demo presents this as:

```text
THISHMIDATA
→ selected fields
→ window-based feature preview
→ thisfeature
→ model run input
```

### 4.4 Connecting algorithm selection to model run policy

Another challenge was connecting algorithm selection to model execution.

The platform needed to show which algorithm was active for the selected dataset and how that policy related to later model runs.

The public demo represents this with synthetic policy and algorithm metadata:

* `tmst_algo_mst`
* `tmst_algo_dtl`
* `tmst_map_algo`
* `tmst_param_mst`
* `tmst_map_algo_param`
* `tmst_model_policy`
* `tmst_model_active`

The demo focuses on making the flow visible rather than providing production-grade tuning.

Supported demo algorithms include:

* Isolation Forest
* AutoEncoder
* Random Forest

### 4.5 Making AI results readable for field users

AI model output is not always easy to understand in raw form.

Anomaly scores, prediction labels, confusion matrix values, and feature importance metrics need to be displayed in a way that operators, analysts, or project reviewers can interpret quickly.

The public demo shows AI results through:

* Anomaly score cards
* Run selector
* Status distribution
* Health index values
* Anomaly result table
* Threshold alert list
* Supervised learning metrics
* Confusion matrix
* Prediction distribution
* AI overview dashboard

The purpose is to present model results as an operational insight layer rather than raw machine learning output.

### 4.6 Separating production logic from public demo logic

A major constraint was creating a public portfolio repository without exposing production information.

The public demo intentionally excludes:

* Production source code
* Production data
* Real equipment identifiers
* Real customer or facility names
* Real database connection values
* Server addresses
* Runtime logs
* Model artifacts
* Private deployment configuration
* Private repository history

Instead, the repository uses synthetic identifiers, deterministic seed data, and local-only services.

## 5. Solution

The solution was to structure the system as a dashboard-centered manufacturing AI workflow.

The public demo uses:

* React frontend for operational screens and result review
* Spring Boot API for dataset, dashboard, preprocessing, model run, and result endpoints
* FastAPI AI server for deterministic demo model execution
* MongoDB seed data for synthetic raw records, features, model runs, anomaly results, alerts, and supervised metrics

The Spring Boot API acts as the facade between the frontend, MongoDB, and the AI server.

Conceptually, the system works like this:

```text
React UI
→ Spring Boot API
→ MongoDB demo dataset
→ FastAPI AI server
→ result collections
→ dashboard result screens
```

## 6. Architecture

The public demo architecture is structured as follows:

```text
Portfolio Reviewer
        ↓
React ML Studio / Insight View UI
        ↓
Spring Boot Demo API
        ↓
MongoDB Synthetic Dataset
        ↓
FastAPI Demo AI Server
```

Main runtime components:

| Component                   | Role                            |
| --------------------------- | ------------------------------- |
| `apps/web`                  | React + TypeScript dashboard UI |
| `apps/api`                  | Spring Boot API and demo facade |
| `apps/ai-server`            | FastAPI model execution service |
| `demo-data/seed`            | Synthetic seed JSON records     |
| `scripts/seed-demo-data.py` | Seed loader for local MongoDB   |
| `ml_studio_demo`            | Local demo MongoDB database     |

The production architecture followed the same general concept, but included operational data, internal configuration, deployment-specific infrastructure, and private security boundaries that are not included in the public repository.

## 7. Data Pipeline

The public demo uses synthetic collections to represent the AI workflow.

| Pipeline Step         | Demo Collection / Source                          | Description                            |
| --------------------- | ------------------------------------------------- | -------------------------------------- |
| Raw HMI data          | `THISHMIDATA`                                     | Synthetic process records              |
| Equipment master      | `TMSTMC`                                          | Public-safe demo equipment metadata    |
| Dataset configuration | `tmst_dataset_config`                             | Demo dataset registry                  |
| Feature configuration | `tmst_feature_mst`                                | Feature dataset metadata               |
| Feature rows          | `thisfeature`                                     | Synthetic feature-ready rows           |
| Algorithm metadata    | `tmst_algo_mst`, `tmst_algo_dtl`, `tmst_map_algo` | Demo algorithm catalog                 |
| Parameter metadata    | `tmst_param_mst`, `tmst_map_algo_param`           | Demo algorithm parameter metadata      |
| Model policy          | `tmst_model_policy`, `tmst_model_active`          | Active demo model policy               |
| Model run history     | `thismodelrun`                                    | Synthetic run metadata                 |
| Anomaly results       | `thisanomalyresult`                               | Isolation Forest / anomaly result rows |
| Threshold alerts      | `thisthresholdalert`                              | Synthetic threshold alert rows         |
| Supervised results    | `thisclassificationresult`, `thismodeleval`       | Random Forest result and metric rows   |

The canonical public demo dataset key is:

```text
DEMO_DATASET_MANUFACTURING_AI
```

## 8. AI Analysis Flow

The demo presents the AI workflow through several screens.

### AI Overview

The AI Overview screen summarizes active demo models, recent model run state, demo dataset status, and signal highlights.

### Data Exploration

The Data Exploration screen provides time-series inspection for synthetic process fields. It helps users review raw process trends before model execution.

### Preprocess / Feature Engineering

The Preprocess screen shows the transition from raw rows to selected feature fields and feature preview.

This demonstrates the idea that manufacturing AI workflows require structured preprocessing before model execution.

### Algorithm Selection

The Algorithm Selection screen shows demo algorithm options such as Isolation Forest, AutoEncoder, and Random Forest.

It also introduces the idea of selecting an active policy for a dataset.

### Model Training / Run Policy

The Model Training screen shows the selected dataset, active policy, feature dataset configuration, selected columns, and demo model run context.

In the public demo, this is deterministic and local-only.

### Anomaly Detection

The Anomaly Detection screen shows run selection, anomaly score, health index, status distribution, and result table.

This screen demonstrates how model output can be translated into an operational result view.

### Threshold Alert

The Threshold Alert screen shows synthetic threshold events and acknowledgement status.

### Supervised Learning Result

The Supervised Learning Result screen shows synthetic classification metrics, confusion matrix, prediction distribution, feature importance, and prediction rows.

## 9. Key Features

The public demo includes:

* Demo login flow
* Home dashboard
* AI operation overview
* Synthetic manufacturing dataset selection
* Time-series data exploration
* Raw data preview
* Feature engineering preview
* Algorithm selection
* Model run policy review
* Anomaly result table
* Threshold alert list
* Supervised learning metric dashboard
* Synthetic MongoDB seed loader
* Public safety scan script
* Mermaid architecture and pipeline diagrams
* Public release documentation

## 10. My Role

My responsibilities included:

* Analyzing the manufacturing AI workflow and translating it into a public demo structure.
* Reviewing how raw process data should move through filtering, feature generation, model execution, and result visualization.
* Designing the synthetic dataset structure for public release.
* Defining public-safe dataset keys, equipment IDs, run IDs, and result identifiers.
* Implementing and validating the local demo workflow.
* Building React screens for AI overview, preprocessing, algorithm selection, model run, anomaly result, threshold alert, supervised result, and time-series exploration.
* Implementing Spring Boot demo APIs for dataset, dashboard, model, result, and preprocessing workflows.
* Connecting the demo API structure to a FastAPI AI execution service.
* Preparing MongoDB seed data for repeatable local screenshots.
* Removing or avoiding production-specific identifiers and private runtime values.
* Documenting the architecture, security boundary, AI pipeline, and local runtime.
* Rebuilding the public repository as a synthetic portfolio project rather than copying private project source code.

## 11. Results

The public demo provides an executable local manufacturing AI workflow.

It demonstrates:

* A full raw-to-result AI analytics pipeline
* A local Spring Boot + React + FastAPI architecture
* MongoDB-backed synthetic manufacturing data
* Feature preview and model policy concepts
* Anomaly detection result visualization
* Threshold alert review
* Supervised learning result review
* AI operation dashboard structure
* Portfolio-safe documentation and screenshots

The result is not a production ML platform, but a public portfolio artifact that communicates the core system design and learning process.

## 12. Public Demo Relationship

This repository is not the production project.

It is a sanitized rebuild designed to demonstrate the same engineering concepts:

* Manufacturing AI analytics architecture
* Raw data exploration
* Preprocessing and feature engineering
* Dataset and algorithm policy management
* Model run result tracking
* Anomaly detection review
* Threshold and supervised learning result review
* Synthetic data generation
* Local runtime testing
* Public release safety boundaries

The following are excluded:

* Production source code
* Production database connections
* Customer information
* Real equipment data
* Real process data
* Server addresses
* Private access material
* Logs
* Model artifacts
* Private Git history
* Production screenshots

## 13. Learning Notes

This project also reflects a practical learning process around manufacturing AI modeling.

The learning focus was not only how to run a model, but how to prepare manufacturing data so that model results can be understood and reviewed through a system.

Key learning points:

* Manufacturing data must be interpreted before modeling.
* Raw records should be separated from feature-ready datasets.
* Feature generation rules should be visible and repeatable.
* Algorithm selection should be connected to model run policy.
* AI results need dashboard-level explanation.
* Anomaly detection and supervised learning require different result views.
* Public portfolio versions should use synthetic data and sanitized architecture.

The project helped connect manufacturing AI study topics with an implemented web-based demo.

## 14. Lessons Learned

Key lessons from the project:

* Manufacturing AI platforms require clear separation between raw data, features, model runs, and results.
* Data exploration is necessary before preprocessing and model execution.
* Preprocessing and filtering rules should be made visible to users.
* AI result screens should explain model output in operational terms.
* Synthetic public demos are safer and more maintainable than copied production repositories.
* Local runtime documentation improves portfolio credibility.
* A portfolio project should show not only screens, but also the data and model flow behind those screens.
