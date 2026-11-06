"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { api, apiError } from "@/lib/api";
import type { CurrentUser, Role } from "@/types/api";
import { homePathFromApi } from "@/lib/auth";

type AuthContextValue = {
  user: CurrentUser | null;
  loading: boolean;
  error: string | null;
  login: (identifier: string, password: string) => Promise<void>;
  logout: (force?: boolean) => Promise<void>;
  refresh: () => Promise<CurrentUser | null>;
  can: (...roles: Role[]) => boolean;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const router = useRouter();
  const sessionChecked = useRef(false);

  const refresh = useCallback(async (): Promise<CurrentUser | null> => {
    try {
      const { data } = await api.get<CurrentUser>("/auth/me");
      setUser(data);
      setError(null);
      return data;
    } catch (requestError) {
      setUser(null);
      const failure = apiError(requestError);
      if (failure.status && failure.status !== 401) {
        setError(failure.message);
      }
      if (failure.status === 401 && sessionChecked.current) {
        router.replace("/login?expired=1");
      }
      return null;
    } finally {
      setLoading(false);
      sessionChecked.current = true;
    }
  }, [router]);

  useEffect(() => {
    void refresh();
    const onExpired = () => {
      setUser(null);
      router.replace("/login?expired=1");
    };
    window.addEventListener("stockly:unauthorized", onExpired);
    return () => window.removeEventListener("stockly:unauthorized", onExpired);
  }, [refresh, router]);

  const login = useCallback(async (identifier: string, password: string) => {
    setError(null);
    try {
      const { data } = await api.post<{ role: Role; home: string }>("/auth/login", {
        username: identifier,
        password,
      });
      const currentUser = await refresh();
      if (!currentUser) throw new Error("We could not confirm your session. Please try signing in again.");
      router.replace(homePathFromApi(data.home, data.role));
    } catch (requestError) {
      const failure = apiError(requestError);
      const message = failure.status === 401 || failure.status === 403
        ? "Invalid email or password. Please try again."
        : failure.message;
      setError(message);
      throw new Error(message);
    }
  }, [refresh, router]);

  const logout = useCallback(async (force = false) => {
    try {
      await api.post(`/auth/logout${force ? "?force=true" : ""}`);
      setUser(null);
      router.replace("/login");
    } catch (requestError) {
      const failure = apiError(requestError);
      setError(failure.message);
      throw new Error(failure.message);
    }
  }, [router]);

  const value = useMemo<AuthContextValue>(() => ({
    user,
    loading,
    error,
    login,
    logout,
    refresh,
    can: (...roles) => Boolean(user && roles.includes(user.role)),
  }), [user, loading, error, login, logout, refresh]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
}
