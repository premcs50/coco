"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { homePathForRole } from "@/lib/auth";

export default function HomePage() {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading) router.replace(user ? homePathForRole(user.role) : "/login");
  }, [loading, user, router]);

  return <div className="loading"><span className="spinner" />Loading STOCKLY…</div>;
}
