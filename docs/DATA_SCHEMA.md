# Data Schema

All seed files live under `demo-data/seed` and contain synthetic JSON.

| Seed file | Collection role |
| --- | --- |
| `tmstmc.sample.json` | Demo equipment master for `DEMO-MC-001` through `DEMO-MC-003` |
| `thishmidata.sample.json` | Synthetic HMI/time-series telemetry |
| `tmst_dataset_config.sample.json` | Dataset configuration for `DEMO_DATASET_MANUFACTURING_AI` |
| `tmst_feature_mst.sample.json` | Feature metadata |
| `tmst_model_policy.sample.json` | Model training policy examples |
| `tmst_algo_mst.sample.json` | Algorithm catalog |
| `thisfeature.sample.json` | Generated feature windows |
| `thismodelrun.sample.json` | Model run history |
| `thisanomalyresult.sample.json` | Isolation Forest anomaly outputs |
| `thisthresholdalert.sample.json` | Threshold alert events |
| `thisclassificationresult.sample.json` | Random Forest classification outputs |
| `thismodeleval.sample.json` | Evaluation metrics |

## Main Demo Fields

- Equipment: `mcId`, `mcName`, `siteName`, `lineName`
- Dataset: `datasetId`, `datasetName`, `sourceCollection`, `targetFeatureCollection`
- Telemetry: `eventTime`, `furnace_temp`, `motor_current`, `vibration_rms`, `pressure`, `gas_flow`, `cycle_time`, `op_status`
- Production-like identifiers: `DEMO-LOT-001`, `DEMO-PART-001`
- Runs: `DEMO-RUN-IF-001`, `DEMO-RUN-RF-001`

## Field Policy

Do not add real customer names, facility names, equipment names, lot numbers, part numbers, operator IDs, IP addresses, credentials, internal URLs, or operation logs.
