# Product Interface Direction

The authenticated product uses a calm operational editorial system: flat surfaces, strong information hierarchy,
compact but touch-safe controls, and one dominant action per screen. It adapts Carbon-style enterprise density and
kiranism-style feature ownership without copying either system or replacing the existing tokens.

## Foundations

- Geist is the only interface typeface. Headings use 650–700 weight; body copy uses 400–500.
- The existing semantic blue is structural and navigational. Orange is reserved for the single primary CTA.
- Surfaces are opaque in application screens. Blur is limited to overlays; cards use a border and subtle shadow.
- Spacing follows 4/8px increments. Main content is capped at `max-w-6xl`.
- Controls are at least 44px high, with visible labels and focus rings.

## Workflow authoring

- Templates list status, service scope, latest version, and the next valid action.
- The editor uses an 8+4 grid on desktop: ordered steps at left, version/readiness proof at right.
- Mobile collapses to one column. Dependencies are edited with checkboxes and explicit move buttons; drag is never
  the only interaction.
- Published versions are visibly read-only. Drafts expose save and publish, with publish as the sole primary action.
- Conditions use a fixed field/operator/value DSL. The UI never accepts executable expressions.

## Runtime onboarding

- Every instance exposes current status, progress, next action, blocking reason, owner, deadline, and help context
  when those values exist.
- Locked, available, under-review, completed, skipped, and inapplicable states include text labels and do not rely
  on color alone.
- Client portal and invitations are Phase 4 and are not represented as available actions in Phase 3.

## Responsive and motion

- Validate at 375×812, 812×375, 768×1024, 1024×768, and 1440×1000.
- Tables reflow to bordered rows/cards below tablet width; no horizontal page scrolling.
- Motion is limited to short opacity/translate list reveals and state feedback. Reduced-motion renders final states.
