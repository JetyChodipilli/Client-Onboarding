export type IntegrationState = "READY" | "DEVELOPMENT" | "UNAVAILABLE" | "DISABLED";

export type IntegrationStatusItem = {
  key: string;
  name: string;
  category: string;
  status: IntegrationState;
  provider: string | null;
  enabled: boolean;
  productionReady: boolean;
  message: string;
};

export type IntegrationStatusResponse = {
  integrations: IntegrationStatusItem[];
};
