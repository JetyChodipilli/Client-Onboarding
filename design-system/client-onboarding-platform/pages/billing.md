# Billing and payments

Applies to internal invoices and client payment pages. This page override retains the existing product implementation: Geist, semantic `globals.css` tokens, light/dark themes, flat bordered surfaces, restrained motion and 44px controls. The generated master typography and marketing/glass suggestions do not override the established application shell.

UI/UX Pro Max search: B2B invoice billing payments portal calm accessible dense. Adopt its minimal enterprise hierarchy and avoid decoration; omit marketing proof, font replacements and new palette suggestions.

- Put amount due, amount paid and the onboarding threshold before transaction history.
- Use one primary payment action; keep refunds/manual overrides in explicit reason-and-confirm forms.
- Monetary input accepts decimal strings and converts exactly to integer paise. Show INR consistently.
- Show draft, awaiting confirmation, failed, refunded, paused, unavailable and zero-payment states in words.
- Client pages show progress, next action, blocker, waiting party, due date and available help; separate Your action from Waiting for our team.
- Reflow line items and transaction records into stacked cards at narrow widths. Wrap provider references.
- Preserve keyboard focus, announce action results, provide retry controls and avoid moving payment controls.
- Browser callbacks never claim paid until refreshed backend evidence confirms it.
