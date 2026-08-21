# Frontend Conventions

## Structure

```text
frontend/
├── app/
├── features/
├── components/
│   ├── ui/
│   └── shared/
├── lib/
├── services/
├── hooks/
├── types/
├── validation/
└── auth/
```

## Rules

- Server/backend authorization is authoritative.
- Keep domain feature code close to the feature that owns it.
- `components/ui` contains reusable visual primitives; `components/shared` contains cross-feature composites.
- Do not create a single product-wide god component.
- Every major screen must handle loading, empty, validation, permission-denied, API-error, and responsive states.
- Forms use explicit schemas and accessible labels/errors.
- Interactive elements need keyboard focus treatment and appropriate accessible names.
- Prefer server components by default; use client components only when interaction/state requires them.

## Client portal UX

Always surface one primary next action and clearly distinguish client action from internal-team waiting states. Locked steps explain prerequisites and errors explain recovery.
