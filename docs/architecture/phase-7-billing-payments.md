# Phase 7 — Billing and Razorpay payments

Phase 7 implements invoices, items, payment policies, partial collections, transactions, refunds,
reconciliation and workflow integration. Razorpay is the user-selected first provider. Contracts and
later phases remain out of scope.

Billing owns invoice content, policy thresholds and derived balances. Payments owns collection
sessions, provider transactions, refunds and webhook receipts. Payments invokes billing's application
boundary inside the same transaction; neither module writes another module's tables. Invoice,
collection-session, payment, refund, onboarding and project states remain separate.

Amounts are integer INR paise. Line tax is rounded half-up per line; totals and threshold calculations
are checked and bounded. Sent invoice content is immutable. Full payment requires the invoice total;
deposit uses a snapshot percentage; milestone uses a snapshot amount. Manual and no-payment policies
are explicit, audited policies, not successful provider payments.

One unresolved collection session per invoice reserves its amount. Repeated commands use the same
idempotency key and fingerprint. Provider calls run outside database transactions. A timeout with an
unknown external outcome preserves the reservation; operators reconcile the provider order by its
immutable local receipt instead of blindly creating another order. Captured partial collections release
their reservation and permit the next collection. Refunds are separately reserved and reconciled.

Razorpay credentials and webhook secrets are deployment secrets, configured independently per tenant.
No API or UI accepts merchant secrets. The production adapter uses only the fixed Razorpay HTTPS API,
bounded responses/timeouts, no redirects and no permissive fallback. Checkout receives only the public
key and server-created order. Its callback is never sufficient proof of payment: the server verifies
the callback signature and fetches the payment from Razorpay. Signed webhooks and server reconciliation
apply the same monotonic payment-state rules.

Webhook processing verifies HMAC over the raw bounded body before parsing, checks event ID/payload hash,
fetches authoritative provider state, checks tenant/order/amount/currency relationships, and commits
the transaction, invoice balance, workflow readiness, audit and outbox together. Duplicate and reordered
events cannot double-count money. Raw provider payloads, card details and secrets are not persisted.

Financial truth must be retained even when a project is paused or cancelled. New checkout is blocked
outside active onboarding. Refunds invalidate a previously satisfied payment requirement; positive
completion is deferred while paused and can be synchronized after resume. Financial events never
activate a project. Event delivery belongs to Phase 10; final review and activation belong to Phase 11.

Validation includes invoice arithmetic, tenant/permission/CSRF checks, callback/webhook forgery,
duplicate and reordered events, concurrent collections/refunds, provider failure and recovery,
clean PostgreSQL migration and browser journeys. Real merchant sandbox certification requires deployed
test credentials and a reachable signed-webhook endpoint; deterministic adapter tests do not claim to
replace that merchant-specific check.

Provider references:
- https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/integration-steps/
- https://razorpay.com/docs/webhooks/validate-test/
- https://razorpay.com/docs/api/orders/create/
- https://razorpay.com/docs/api/refunds/create-normal/
