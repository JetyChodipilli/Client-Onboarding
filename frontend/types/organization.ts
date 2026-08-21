export type Organization = { id: string; name: string; slug: string; status: string; version: number };
export type Role = { id: string; code: string; name: string; description: string | null; systemRole: boolean; permissions: string[]; version: number };
export type Permission = { id: string; code: string; category: string; description: string };
export type OrganizationUser = { membershipId: string; userId: string; email: string; displayName: string; status: string; roles: Array<{id:string;code:string;name:string}>; version: number };
export type AuditLog = { id:string; actorUserId:string|null; action:string; entityType:string; entityId:string|null; beforeState:unknown; afterState:unknown; requestId:string; correlationId:string; occurredAt:string };
