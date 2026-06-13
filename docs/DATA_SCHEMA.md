# Data Schema

The demo uses MongoDB collections that mirror an ML analytics workflow. Collection names below are public demo names only.

## Collections

- `tmstmc`: demo equipment master records with `mcId`, `mcName`, `siteName`, and `lineName`.
- `thishmidata`: synthetic HMI telemetry with timestamps, equipment IDs, status, and numeric sensor fields.
- `thisfeature`: generated feature windows with aggregate statistics.
- `thisanomalyresult`: anomaly model outputs with scores and top contributing signals.
- `thisclassificationresult`: supervised learning metrics and feature importance.
- `thisthresholdalert`: threshold alert events and acknowledgement status.
- `tmst_algo_mst`: demo algorithm catalog.
- `tmst_model_policy`: model training policy examples.

## Field Policy

All identifiers must use demo-safe values such as `DEMO-MC-001`, `DEMO_DATASET_001`, `DEMO_RUN_001`, `DEMO_USER`, `DEMO-LOT-001`, and `DEMO-PART-001`.

Do not use real operation database names, customer names, facility names, equipment names, operator IDs, product serials, lot numbers, IP addresses, or credentials in this repository.
