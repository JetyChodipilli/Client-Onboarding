# ADR-0006: Provider Abstractions

**Status:** Accepted

## Decision

Payment, e-signature, email, malware scanning, and object-storage integrations sit behind explicit application/domain ports and infrastructure adapters. Provider choice must not leak across core business logic.
