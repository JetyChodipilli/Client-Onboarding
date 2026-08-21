# ADR-0001: Modular Monolith for MVP

**Status:** Accepted

## Decision

Implement the MVP as a Spring Boot modular monolith with explicit domain module boundaries.

## Rationale

The PRD prioritizes consistency, transactions, lower operational complexity, and scale without premature microservices. Modules expose controlled application interfaces and do not mutate another module's tables directly.

## Consequence

Service decomposition remains possible later because ownership boundaries and events are explicit, but no network boundary is introduced merely for organizational separation.
