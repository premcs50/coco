export type Role = "ADMIN" | "STORE_MANAGER" | "INVENTORY_STAFF" | "BILLING_STAFF";

export type CurrentUser = {
  username: string;
  email: string;
  role: Role;
  active: boolean;
  lastLoginAt: string;
};

export type ApiRecord = Record<string, unknown> & {
  id?: number | string;
};

export type ApiErrorBody = {
  status?: number;
  error?: string;
  message?: string;
};

export type ApiFailure = {
  message: string;
  status?: number;
};
