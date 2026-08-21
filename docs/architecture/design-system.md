# Design-System Direction

The product uses a restrained professional B2B SaaS visual system across internal operations and the client portal.

## Core principles

- strong hierarchy before decoration;
- one obvious primary action in each task area;
- semantic state labels paired with text, not color alone;
- compact but readable operational density;
- consistent panels, badges, field controls, empty/error/loading/permission states;
- visible focus and keyboard-operable controls;
- mobile-safe wrapping/overflow and WCAG 2.1 AA target.

## Client/onboarding UX

Always communicate current status, progress, next action, blocking prerequisite, waiting party and recovery/help path. Explicitly distinguish **Your Action** from **Waiting for Our Team**.

## Phase 10 patterns

- task queue emphasizes owner/state/due date rather than decorative metrics;
- notification center treats in-app/email delivery as traceable side effects, not business state;
- reminder UI explains bounded cadence and suppression rather than implying unlimited nagging.

## Phase 11 patterns

The final-review surface visually separates:

1. mathematical readiness evidence (`blockingCompleted / blockingTotal`);
2. reviewer actions (start review, approve, request revision);
3. project READY as an approved-but-not-active state;
4. the separate privileged activation action.

Payment and signed-contract requirements are presented as final/non-reopenable facts; only owning feature types with supported revision semantics can be selected for rework.
