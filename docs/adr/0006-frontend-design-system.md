# ADR 0006: Token-Driven Next.js UI Foundation

- Status: Accepted
- Date: 2026-08-20

## Decision

Use Next.js App Router, TypeScript, Tailwind CSS and shadcn/ui-compatible local components. Semantic CSS tokens own color, spacing, radius, elevation and motion. Geist is the local-first typography baseline, chosen to preserve the recommended modern B2B SaaS character without a runtime font-network dependency.

## Consequences

Pages remain consistent, themeable, responsive and accessible. Raw colors and arbitrary one-off spacing are prohibited in business components. Motion is optional, restrained and disabled when reduced motion is requested.

