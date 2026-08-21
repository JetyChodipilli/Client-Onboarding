export type ClientStatus = "PROSPECT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";

export type Client = {
  id: string;
  name: string;
  status: ClientStatus;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type ClientContact = {
  id: string;
  clientId: string;
  displayName: string;
  email: string;
  jobTitle: string | null;
  phone: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};
