/**
 * Tenant-scoped operational reporting read model. Reporting may read the PostgreSQL source-of-truth across
 * module-owned tables but must never mutate sibling modules or omit organization/resource predicates.
 */
package com.brainserve.onboarding.reporting;
