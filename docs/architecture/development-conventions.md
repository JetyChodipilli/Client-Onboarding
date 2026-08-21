# Development Conventions

## Branching

Use short-lived branches from `develop` during active integration and promote reviewed, green changes to `main`. Keep releases taggable from `main`. Emergency fixes branch from the production line and are merged back into active development.

Recommended names:

- `feature/phase-<n>-<scope>`
- `fix/<scope>`
- `security/<scope>`
- `chore/<scope>`

Do not mix multiple implementation phases in one feature branch unless the user explicitly changes the phase boundary.

## Java

- Java 21 language level.
- Constructor injection; avoid field injection.
- Thin controllers; use-case/transaction logic in application services.
- Immutable request/response/value types where practical.
- No persistence entity returned directly from an API.
- No catch-and-ignore exception handling.
- Stable domain error codes; no sensitive internal details in client responses.
- Treat compiler warnings as review findings; keep `-Xlint:all` enabled.

## TypeScript / React

- TypeScript `strict` mode.
- Prefer Server Components unless interaction/browser state requires a Client Component.
- No `any` without a narrow, documented integration-boundary reason.
- Keep feature code with the owning feature; promote a component to shared only after genuine reuse.
- ESLint `core-web-vitals` + TypeScript rules must pass with zero warnings.

## Formatting and files

`.editorconfig` is the repository baseline. Generated/build output, local environments, logs, secrets, IDE metadata, Playwright output, and dependency directories are not committed.

## Database

Every schema change is a new immutable Flyway migration. Never use an application startup to mutate production schema outside Flyway.

## Review gate

Each pull request should state:

1. requested phase and PRD requirement(s),
2. migrations/endpoints/screens changed,
3. tests added or changed,
4. tenant/authorization impact,
5. security impact,
6. backward-compatibility or rollout concerns,
7. verification commands/results.
