"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Loader2, Plus, RefreshCw, Settings2, UserRound } from "lucide-react";
import { useAuth } from "@/auth/auth-provider";
import { FormMessage } from "@/components/auth/form-message";
import { PageHeading } from "@/components/layout/page-heading";
import { EmptyState } from "@/components/shared/empty-state";
import { PermissionState } from "@/components/shared/permission-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiClientError } from "@/services/api-client";
import type { OrganizationUser, Role } from "@/types/organization";

export default function UsersPage() {
  const { authorizedRequest, hasPermission, user } = useAuth();
  const canRead = hasPermission("USER_READ") || hasPermission("USER_MANAGE");
  const canManage = hasPermission("USER_MANAGE");
  const [users, setUsers] = useState<OrganizationUser[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [loading, setLoading] = useState(true);
  const [showInvite, setShowInvite] = useState(false);
  const [editing, setEditing] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [memberRows, roleRows] = await Promise.all([
        authorizedRequest<OrganizationUser[]>("/organization-users?size=100"),
        canManage || hasPermission("ROLE_READ")
          ? authorizedRequest<Role[]>("/roles")
          : Promise.resolve([]),
      ]);
      setUsers(memberRows);
      setRoles(roleRows);
    } catch (requestError) {
      setError(requestError instanceof ApiClientError ? requestError.message : "People could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [authorizedRequest, canManage, hasPermission]);

  useEffect(() => {
    if (canRead) void load();
  }, [canRead, load]);

  const delegableRoles = useMemo(
    () => roles.filter((role) => role.permissions.every((permission) => user?.permissions.includes(permission))),
    [roles, user?.permissions],
  );

  if (!canRead) return <PermissionState />;

  return (
    <>
      <PageHeading
        eyebrow="Identity"
        title="People & memberships"
        description="Manage tenant membership, roles, and access status without exposing another organization’s identities."
        actions={
          <div className="flex gap-2">
            <Button variant="secondary" size="icon" onClick={() => void load()} aria-label="Refresh people">
              <RefreshCw className="size-4" />
            </Button>
            {canManage && (
              <Button onClick={() => setShowInvite((value) => !value)}>
                <Plus className="size-4" /> Invite person
              </Button>
            )}
          </div>
        }
      />

      {showInvite && (
        <InvitePanel
          roles={delegableRoles}
          onDone={() => {
            setShowInvite(false);
            void load();
          }}
        />
      )}
      {error && <div className="mb-5"><FormMessage>{error}</FormMessage></div>}

      <section className="panel overflow-hidden">
        <div className="border-b px-5 py-4">
          <p className="text-sm font-semibold">Organization members</p>
          <p className="mt-1 text-xs text-[hsl(var(--muted-foreground))]">{users.length} visible memberships</p>
        </div>
        {loading ? (
          <div className="flex items-center justify-center py-16 text-sm text-[hsl(var(--muted-foreground))]">
            <Loader2 className="mr-2 size-4 animate-spin" /> Loading people…
          </div>
        ) : users.length === 0 ? (
          <EmptyState title="No members yet" description="Invite your first teammate and assign only the access they need." />
        ) : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead><tr><th>Person</th><th>Status</th><th>Roles</th>{canManage && <th className="w-28">Actions</th>}</tr></thead>
              <tbody>
                {users.map((member) => (
                  <MemberRows
                    key={member.membershipId}
                    member={member}
                    canManage={canManage}
                    isSelf={member.userId === user?.id}
                    roles={roles}
                    heldPermissionCodes={user?.permissions ?? []}
                    editing={editing === member.membershipId}
                    onToggle={() => setEditing((current) => current === member.membershipId ? null : member.membershipId)}
                    onDone={() => {
                      setEditing(null);
                      void load();
                    }}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}

function MemberRows({ member, canManage, isSelf, roles, heldPermissionCodes, editing, onToggle, onDone }: {
  member: OrganizationUser;
  canManage: boolean;
  isSelf: boolean;
  roles: Role[];
  heldPermissionCodes: string[];
  editing: boolean;
  onToggle: () => void;
  onDone: () => void;
}) {
  return (
    <>
      <tr>
        <td>
          <div className="flex items-center gap-3">
            <div className="grid size-9 place-items-center rounded-full bg-[hsl(var(--surface-subtle))]">
              <UserRound className="size-4 text-[hsl(var(--muted-foreground))]" />
            </div>
            <div><p className="font-medium">{member.displayName}</p><p className="mt-0.5 text-xs text-[hsl(var(--muted-foreground))]">{member.email}</p></div>
          </div>
        </td>
        <td><Badge variant={member.status === "ACTIVE" ? "success" : "warning"}>{member.status}</Badge></td>
        <td><div className="flex flex-wrap gap-1.5">{member.roles.map((role) => <Badge key={role.id}>{role.name}</Badge>)}</div></td>
        {canManage && <td><Button variant="ghost" size="sm" onClick={onToggle}><Settings2 className="size-4" />Manage</Button></td>}
      </tr>
      {canManage && editing && (
        <tr>
          <td colSpan={4} className="!p-0">
            <MemberEditor member={member} roles={roles} heldPermissionCodes={heldPermissionCodes} isSelf={isSelf} onDone={onDone} />
          </td>
        </tr>
      )}
    </>
  );
}

function MemberEditor({ member, roles, heldPermissionCodes, isSelf, onDone }: { member: OrganizationUser; roles: Role[]; heldPermissionCodes: string[]; isSelf: boolean; onDone: () => void }) {
  const { authorizedRequest } = useAuth();
  const [selected, setSelected] = useState(member.roles.map((role) => role.id));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function saveRoles() {
    setBusy(true); setError(null);
    try {
      await authorizedRequest(`/organization-users/${member.membershipId}/roles`, {
        method: "PUT",
        body: JSON.stringify({ roleIds: selected, version: member.version }),
      });
      onDone();
    } catch (requestError) {
      setError(requestError instanceof ApiClientError ? requestError.message : "Roles could not be updated.");
    } finally { setBusy(false); }
  }

  async function toggleStatus() {
    setBusy(true); setError(null);
    try {
      await authorizedRequest(`/organization-users/${member.membershipId}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status: member.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE", version: member.version }),
      });
      onDone();
    } catch (requestError) {
      setError(requestError instanceof ApiClientError ? requestError.message : "Membership status could not be updated.");
    } finally { setBusy(false); }
  }

  return (
    <div className="border-y bg-[hsl(var(--surface-subtle))] px-5 py-5">
      <div className="grid gap-5 lg:grid-cols-[1fr_auto]">
        <fieldset>
          <legend className="text-sm font-semibold">Assigned roles</legend>
          <p className="mt-1 text-xs text-[hsl(var(--muted-foreground))]">Existing roles remain visible and can be removed. A new role can be assigned only when you hold every permission it grants.</p>
          <div className="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
            {roles.map((role) => {
              const isSelected = selected.includes(role.id);
              const canAdd = role.permissions.every((permission) => heldPermissionCodes.includes(permission));
              const disabled = !isSelected && !canAdd;
              return (
                <label key={role.id} className={`flex items-start gap-3 rounded-xl border bg-white p-3 ${disabled ? "cursor-not-allowed opacity-60" : "cursor-pointer"}`}>
                  <input type="checkbox" className="mt-1" checked={isSelected} disabled={disabled} onChange={(event) => setSelected((current) => event.target.checked ? [...current, role.id] : current.filter((id) => id !== role.id))} />
                  <span><span className="flex flex-wrap items-center gap-2"><span className="text-sm font-medium">{role.name}</span>{disabled && <span className="text-[10px] font-semibold uppercase tracking-wide text-[hsl(var(--muted-foreground))]">Cannot add</span>}</span><span className="mt-0.5 block font-mono text-[11px] text-[hsl(var(--muted-foreground))]">{role.code}</span></span>
                </label>
              );
            })}
          </div>
        </fieldset>
        <div className="flex min-w-44 flex-col justify-end gap-2">
          <Button onClick={() => void saveRoles()} disabled={busy || selected.length === 0}>{busy && <Loader2 className="size-4 animate-spin" />}Save roles</Button>
          <Button variant="secondary" onClick={() => void toggleStatus()} disabled={busy || isSelf}>{member.status === "ACTIVE" ? "Suspend access" : "Reactivate access"}</Button>
          {isSelf && <p className="text-center text-[11px] text-[hsl(var(--muted-foreground))]">You cannot suspend yourself.</p>}
        </div>
      </div>
      {error && <div className="mt-4"><FormMessage>{error}</FormMessage></div>}
    </div>
  );
}

function InvitePanel({ roles, onDone }: { roles: Role[]; onDone: () => void }) {
  const { authorizedRequest } = useAuth();
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError(null);
    try {
      await authorizedRequest("/organization-users/invitations", { method: "POST", body: JSON.stringify({ email, displayName, roleIds: selected }) });
      onDone();
    } catch (requestError) {
      setError(requestError instanceof ApiClientError ? requestError.message : "Invitation could not be sent.");
    } finally { setBusy(false); }
  }

  return (
    <section className="panel mb-6 p-5 sm:p-6">
      <div className="mb-5"><h2 className="font-semibold">Invite a teammate</h2><p className="mt-1 text-sm text-[hsl(var(--muted-foreground))]">The single-use invitation expires automatically and carries only the selected tenant roles.</p></div>
      <form onSubmit={submit} className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-2"><Label htmlFor="invite-name">Full name</Label><Input id="invite-name" value={displayName} onChange={(event) => setDisplayName(event.target.value)} required /></div>
        <div className="space-y-2"><Label htmlFor="invite-email">Work email</Label><Input id="invite-email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></div>
        <fieldset className="lg:col-span-2"><legend className="mb-2 text-sm font-medium">Roles</legend><div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">{roles.map((role) => <label key={role.id} className="flex cursor-pointer items-start gap-3 rounded-xl border bg-white p-3 hover:bg-[hsl(var(--surface-subtle))]"><input className="mt-1" type="checkbox" checked={selected.includes(role.id)} onChange={(event) => setSelected((current) => event.target.checked ? [...current, role.id] : current.filter((id) => id !== role.id))} /><span><span className="block text-sm font-medium">{role.name}</span><span className="mt-1 block text-xs text-[hsl(var(--muted-foreground))]">{role.permissions.length} permissions</span></span></label>)}</div></fieldset>
        {error && <div className="lg:col-span-2"><FormMessage>{error}</FormMessage></div>}
        <div className="flex justify-end lg:col-span-2"><Button disabled={busy || selected.length === 0}>{busy ? <Loader2 className="size-4 animate-spin" /> : <Plus className="size-4" />}Send invitation</Button></div>
      </form>
    </section>
  );
}
