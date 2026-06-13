# Security

This public demo repository is sanitized for portfolio use.

## Public Release Policy

- No credentials, tokens, API keys, private certificates, or password values.
- No production database URI or private service endpoint.
- No real IP address, host, internal URL, or deployment path.
- No real customer, facility, equipment, lot, part, operator, or log data.
- No pickle, joblib, or trained model artifact files.
- `.env` is ignored; `.env.example` contains localhost-only dummy values.

## Local Configuration

Runtime values should be provided through environment variables. The default values are suitable for local demo execution only.

## Safety Scan

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/scan-public-safety.ps1
```

The scanner prints file names and line numbers only and does not echo matched values.

## Demo Limitation

Authentication and authorization are simplified for portfolio demonstration. Harden request authentication, authorization, audit logging, rate limiting, and secret handling before adapting the code for real deployment.
