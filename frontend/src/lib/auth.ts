import type { Role } from "@/types/api";

export function homePathForRole(role: Role): string {
  switch (role) {
    case "ADMIN":
    case "STORE_MANAGER":
      return "/dashboard";
    case "INVENTORY_STAFF":
      return "/inventory";
    case "BILLING_STAFF":
      return "/invoices";
  }
}

export function homePathFromApi(home: string | undefined, role: Role): string {
  if (home === "/api/dashboard") return "/dashboard";
  if (home === "/api/inventory/stock") return "/inventory";
  if (home === "/api/invoices") return "/invoices";
  return homePathForRole(role);
}
