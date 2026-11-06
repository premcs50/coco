"use client";

import { useEffect } from "react";
import { useParams, useRouter } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import { ModuleWorkspace } from "@/components/ModuleWorkspace";
import { useAuth } from "@/context/AuthContext";
import { homePathForRole } from "@/lib/auth";
import type { Role } from "@/types/api";

const moduleRoles: Record<string, Role[]> = {
  invoices: ["ADMIN", "STORE_MANAGER", "BILLING_STAFF"],
  purchases: ["ADMIN", "STORE_MANAGER", "INVENTORY_STAFF"],
  shifts: ["ADMIN", "STORE_MANAGER", "BILLING_STAFF"],
  analytics: ["ADMIN", "STORE_MANAGER"],
  audit: ["ADMIN"],
  users: ["ADMIN"],
  owners: ["ADMIN"],
  settings: ["ADMIN"],
};

export default function ModulePage() {
  const params = useParams<{ module: string }>();
  const moduleName = Array.isArray(params.module) ? params.module[0] : params.module;
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) router.replace("/login");
    else if (!loading && user && moduleRoles[moduleName]?.includes(user.role) === false) {
      router.replace(homePathForRole(user.role));
    }
  }, [loading, user, router, moduleName]);

  if (loading || !user || moduleRoles[moduleName]?.includes(user.role) === false) return <div className="loading"><span className="spinner" />Loading STOCKLY…</div>;
  if (!moduleName) return <div className="loading">Page not found.</div>;
  return <AppShell><ModuleWorkspace module={moduleName} /></AppShell>;
}
