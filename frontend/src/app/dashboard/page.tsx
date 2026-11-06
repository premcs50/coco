"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import { Dashboard } from "@/components/Dashboard";
import { useAuth } from "@/context/AuthContext";
import { homePathForRole } from "@/lib/auth";

export default function DashboardPage() {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) router.replace("/login");
    else if (!loading && user && !["ADMIN", "STORE_MANAGER"].includes(user.role)) {
      router.replace(homePathForRole(user.role));
    }
  }, [loading, user, router]);

  if (loading || !user || !["ADMIN", "STORE_MANAGER"].includes(user.role)) {
    return <div className="loading"><span className="spinner" />Loading STOCKLY…</div>;
  }
  return <AppShell><Dashboard /></AppShell>;
}
