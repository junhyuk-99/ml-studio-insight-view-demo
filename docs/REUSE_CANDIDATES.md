# Reuse Candidates

## Reusable Modules

- React dashboard layout, route configuration, and service layer
- Dataset selection and field selection utilities
- Spring Boot response wrapper and exception handling
- Dynamic MongoDB schema resolution pattern
- FastAPI model execution router structure
- Model policy, result, and alert domain shapes

## Extension Notes

- Replace demo collections with a clean domain-specific schema before production use.
- Add formal authentication and authorization for real deployments.
- Add test fixtures around model execution and collection schema resolution.
- Keep generated features and model outputs versioned by dataset and run ID.

## Future Improvements

- Add synthetic data generator scripts
- Add integration tests for API and AI-server calls
- Add docker-compose for local MongoDB, API, AI, and web startup
- Add API contract examples generated from OpenAPI
