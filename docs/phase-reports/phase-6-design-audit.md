# Phase 6 UI and complexity review

The UI reuses the established Geist typography, semantic colors, native inputs and shared cards/buttons.
Client file collection and internal review share one panel with explicit permission-driven actions. Requirement
management remains a small feature folder; there is no generic file-manager framework or new frontend dependency.

## Changes from the review

- Show status, next action, project progress, deadline, waiting party, blocker and help above file controls.
- Separate client action from waiting for the team; do not offer approval or unsafe downloads to clients.
- Use a labeled native file input, visible type/size limits, upload progress, cancel and a retry path.
- Require revision feedback; preserve replacement and review history. Deliver files as attachments, not inline previews.
- Focus actionable error messages and expose loading/live status. Clear the native input after successful upload.
- Wrap long names and use a 7+5 desktop split that stacks on small screens. No essential action depends on animation.
- Make scan failure/quarantine feedback an error state, not a green success message.
- Distinguish completed onboarding awaiting internal review from paused or closed work.
- Replace offscreen translated skip links with screen-reader-only hiding and a visible keyboard-focus state;
  full-page captures had exposed the translated links over scrolled content. Verify keyboard entry into the portal.
- Update stale Phase 4/5 labels on the foundation page and API metadata to Phase 6.

## Validation

Lint, strict types and 18 frontend unit tests pass locally. Playwright covers 1440×1000, 768×1024,
375×812 and 812×375, including overflow, browser console, denied/empty/loading and failure states.
Implementation CI run 36381813000 passed all 129 browser scenarios with three intentional duplicate-bootstrap
skips and no retries. Inspected the live approved-file desktop capture, mobile submission/quarantine, tablet
review and landscape requirement screens. Text and controls reflow without horizontal overflow. The skip-link
correction identified from those captures is included in the final revision and must pass the same CI gate.

## Ponytail review

Storage and scanner ports are required boundaries with production adapters and isolated test doubles. Scan I/O
has its own service because it must run outside project/database locks. Existing portal and step execution
services own access and workflow writes; the asset module does not introduce a second authorization engine.
The source-built storage fixture replaces a demonstrably unavailable registry dependency.

No speculative framework or unused extension point was identified. Lean already. Ship after verification.
