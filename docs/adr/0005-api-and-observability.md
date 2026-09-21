# ADR 0005: REST API, Standard Envelopes and Correlation

- Status: Accepted
- Date: 2026-08-20

## Decision

Expose versioned REST endpoints under `/api/v1`, document them with OpenAPI, and use consistent success/error envelopes. Propagate request and correlation IDs through response headers, MDC and structured logs.

## Consequences

Clients receive stable error codes and field errors. Operations can trace synchronous and asynchronous work without exposing secrets or provider payloads.

