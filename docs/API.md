# API

This document summarizes the public demo API surface. Endpoint details reflect the copied implementation where available and should be treated as demo-level documentation rather than a production contract.

## Main Groups

- `/api/auth`: login and user session context
- `/api/users`: demo user management and password change flows
- `/api/home`: dashboard KPIs and recent activity
- `/api/equipment`: equipment master options
- `/api/data-exploration`: histogram, boxplot, timeseries, correlation, and process flow data
- `/api/preprocess`: data source options, raw previews, and feature generation
- `/api/model-train`: model run creation, execution, policy, and AI overview data
- `/api/threshold-alert`: threshold alert summary, list, recalculation, and acknowledgement
- `/api/model/execute`: FastAPI model execution endpoints

## Request And Response Shape

The Spring Boot API generally wraps responses in an `ApiResponse` object with `ok`, `message`, `data`, and optional `errorCode` fields. The frontend sends JSON and uses demo authentication headers loaded from browser storage for protected routes.

## Demo Notes

Use local URLs from `.env.example`:

- Web: `http://localhost:5173`
- API: `http://localhost:8090`
- AI server: `http://localhost:8001`
- MongoDB: `mongodb://localhost:27017/demo_ml_studio_db`
