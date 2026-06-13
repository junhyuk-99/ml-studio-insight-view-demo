# ML Studio Demo Case Study

## Problem

Manufacturing analytics teams often need to inspect telemetry, prepare features, execute models, and review results across disconnected tools. That makes anomaly review, threshold monitoring, and supervised learning validation slower than it needs to be.

## Approach

This demo presents a dashboard-centered ML Studio / Insight View pattern:

- A React workspace for operational screens and result review.
- A Spring Boot API for dashboard data, model metadata, result summaries, and data exploration queries.
- A FastAPI AI service for deterministic demo model execution.
- Synthetic seed collections that resemble telemetry, features, model runs, alerts, and evaluation outputs.

## Data Pipeline

1. Synthetic HMI rows are represented in `thishmidata`.
2. Feature windows are represented in `thisfeature`.
3. Model run metadata is represented in `thismodelrun`.
4. Isolation Forest outputs are represented in `thisanomalyresult`.
5. Random Forest outputs are represented in `thisclassificationresult` and `thismodeleval`.
6. Alert review uses `thisthresholdalert`.

## AI Analysis Flow

- AI Overview summarizes active demo models and recent run state.
- Anomaly Detection shows scores, anomaly flags, and health index values.
- Threshold Alert lists limit breaches for synthetic process features.
- Supervised Learning Result shows metrics, feature importance, and predictions.
- Data Exploration provides dataset and timeseries inspection views.

## Dashboard Composition

The frontend keeps the operational shape of a real analytics console: sidebar navigation, header context, dashboard cards, charts, status badges, tables, and fallback states for local review.

## Demo Boundaries

The repository is intentionally scoped to P0 portfolio review. It does not include production data, private deployments, real customer assets, trained model files, or operational secrets.
