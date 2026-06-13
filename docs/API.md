# API

The API is demo-level and public-safe. Spring Boot responses generally use:

```json
{
  "ok": true,
  "data": {},
  "message": "Loaded.",
  "errorCode": null
}
```

## Spring Boot API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/health` | API status |
| GET | `/api/home/dashboard` | Home dashboard KPIs and recent model activity |
| GET | `/api/modeltrain/overview` | AI overview summary |
| GET | `/api/modeltrain/anomaly/runs` | Anomaly run options |
| GET | `/api/modeltrain/anomaly/results` | Anomaly result points |
| GET | `/api/threshold-alert/summary` | Threshold alert summary |
| GET | `/api/threshold-alert/list` | Threshold alert rows |
| GET | `/api/supervised/result/runs` | Supervised run options |
| GET | `/api/supervised/result/summary` | Supervised metrics and feature importance |
| GET | `/api/supervised/result/predictions` | Supervised prediction rows |
| GET | `/api/data-exploration/datasets` | Dataset options |
| GET | `/api/data-exploration/timeseries/fields` | Timeseries field options |
| POST | `/api/data-exploration/timeseries/query` | Timeseries data query |
| GET | `/api/equipment/master` | Demo equipment master |

## AI Server

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/health` | AI server status |
| POST | `/api/model/execute/isolation-forest` | Deterministic anomaly scoring |
| POST | `/api/model/execute/random-forest` | Deterministic supervised predictions |

## Example AI Request

```json
{
  "algorithm": "ISOLATION_FOREST",
  "rows": [
    {
      "window_start": "2026-06-10T09:00:00Z",
      "window_end": "2026-06-10T09:10:00Z",
      "features": {
        "furnace_temp": 742.4,
        "motor_current": 18.2,
        "vibration_rms": 0.18,
        "pressure": 2.18,
        "gas_flow": 41.7,
        "cycle_time": 48.2
      }
    }
  ]
}
```

## Demo Limitations

Authentication is simplified for demo use. The API shape is intended for portfolio review and local exploration, not as a production contract.
