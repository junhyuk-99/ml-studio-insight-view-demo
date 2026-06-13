# Security

This public demo repository is sanitized for portfolio use.

## Removed Or Excluded

- Production MongoDB and JDBC URLs
- Credentials, tokens, API keys, and access keys
- Internal deployment workflows and IDE settings
- Real logs, dumps, spreadsheets, archives, build outputs, and caches
- Customer, facility, equipment, operator, production, lot, part, and defect identifiers
- Private brand images and logo assets

## Credential Policy

Runtime configuration should come from environment variables. `.env.example` contains dummy localhost values only. Real `.env` files must remain untracked.

## Demo Limitation

Authentication and model execution flows are retained to show application structure. Review and harden them before adapting this code for a real deployment.
