export type Organization = { id: string; slug: string; name: string; status: string; createdAt: string; updatedAt: string; version: number };
export type Role = { id: string; organizationId: string; name: string; description: string; permissions: string[]; archivedAt?: string; version: number };
export type Member = { id: string; organizationId: string; userId: string; email: string; displayName: string; roleId: string; roleName: string; status: string; invitedAt: string; joinedAt?: string; version: number };
export type AuditEntry = { id: string; actorUserId?: string; action: string; entityType: string; entityId?: string; source: string; createdAt: string };
