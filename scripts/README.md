# Scripts

Demo-only helper scripts for local setup and public safety checks.

## Seed Data

```bash
python scripts/seed-demo-data.py --dry-run
python scripts/seed-demo-data.py --uri mongodb://localhost:27017 --db ml_studio_demo
```

The loader reads `demo-data/seed/*.sample.json`. If `pymongo` is not installed, it prints install guidance and exits without failing.

## Public Safety Scan

```powershell
powershell -ExecutionPolicy Bypass -File scripts/scan-public-safety.ps1
```

The scan reports file names and line numbers only. It avoids printing matched secret-like values.
