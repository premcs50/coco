"use client";

import {
  Activity, Bell, Boxes, ChevronDown, CircleDollarSign, ClipboardList, CreditCard,
  FileBarChart, LayoutDashboard, LogOut, Menu, Moon, Package, Search, Settings,
  ShieldCheck, ShoppingBasket, Truck, UserRound, Users, Warehouse, X,
} from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useAuth } from "@/context/AuthContext";
import { api, apiError } from "@/lib/api";
import type { ApiRecord, Role } from "@/types/api";

type NavItem = { label: string; href: string; icon: typeof LayoutDashboard; roles?: Role[]; ownerGap?: boolean };
const groups: { title: string; items: NavItem[] }[] = [
  { title: "Workspace", items: [
  { label: "Overview", href: "/dashboard", icon: LayoutDashboard, roles: ["ADMIN", "STORE_MANAGER"] },
    { label: "Billing", href: "/invoices", icon: CreditCard, roles: ["ADMIN", "STORE_MANAGER", "BILLING_STAFF"] },
    { label: "Products", href: "/products", icon: Package },
    { label: "Inventory", href: "/inventory", icon: Warehouse },
  ] },
  { title: "Manage", items: [
    { label: "Purchasing", href: "/purchases", icon: ShoppingBasket, roles: ["ADMIN", "STORE_MANAGER", "INVENTORY_STAFF"] },
    { label: "Customers", href: "/customers", icon: Users },
    { label: "Suppliers", href: "/suppliers", icon: Truck },
    { label: "Shifts", href: "/shifts", icon: CircleDollarSign, roles: ["ADMIN", "STORE_MANAGER", "BILLING_STAFF"] },
  ] },
  { title: "Insights", items: [
    { label: "Reports", href: "/reports", icon: FileBarChart },
    { label: "Analytics", href: "/analytics", icon: Activity, roles: ["ADMIN", "STORE_MANAGER"] },
    { label: "Notifications", href: "/notifications", icon: Bell },
    { label: "Audit log", href: "/audit", icon: ClipboardList, roles: ["ADMIN"] },
  ] },
  { title: "Administration", items: [
    { label: "Staff accounts", href: "/users", icon: UserRound, roles: ["ADMIN"] },
    { label: "Owner management", href: "/owners", icon: ShieldCheck, roles: ["ADMIN"], ownerGap: true },
    { label: "Settings", href: "/settings", icon: Settings, roles: ["ADMIN"] },
  ] },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const { user, logout, can } = useAuth();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [searchResults, setSearchResults] = useState<ApiRecord[] | null>(null);
  const [searchError, setSearchError] = useState("");
  const [dark, setDark] = useState(false);
  const [logoutError, setLogoutError] = useState("");
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    document.documentElement.dataset.theme = dark ? "dark" : "light";
  }, [dark]);

  useEffect(() => {
    const timer = window.setTimeout(async () => {
      if (query.trim().length < 2) {
        setSearchResults(null);
        setSearchError("");
        return;
      }
      try {
        const { data } = await api.get("/search", { params: { q: query.trim() } });
        const groups = data as Record<string, ApiRecord[]>;
        setSearchResults(Object.entries(groups).flatMap(([kind, records]) =>
          records.map((record) => ({ ...record, _kind: kind })),
        ).slice(0, 8));
        setSearchError("");
      } catch (error) {
        setSearchError(apiError(error).message);
      }
    }, 350);
    return () => window.clearTimeout(timer);
  }, [query]);

  const navGroups = groups.map((group) => ({
    ...group,
    items: group.items.filter((item) => !item.roles || item.roles.some((role) => can(role))),
  })).filter((group) => group.items.length > 0);

  const onLogout = async () => {
    try { await logout(); } catch (error) { setLogoutError(apiError(error).message); }
  };

  return (
    <div className="app-shell">
      {sidebarOpen && <div className="drawer-backdrop" onClick={() => setSidebarOpen(false)} />}
      <aside className={`sidebar ${sidebarOpen ? "open" : ""}`}>
        <div className="brand"><div className="brand-mark"><Boxes size={20} /></div><div><div className="brand-name">STOCKLY</div><div className="brand-caption">RETAIL OPERATIONS</div></div><button className="icon-button mobile-menu" aria-label="Close menu" onClick={() => setSidebarOpen(false)}><X size={18} color="white" /></button></div>
        <nav aria-label="Main navigation">
          {navGroups.map((group) => <div key={group.title}>
            <div className="nav-caption">{group.title}</div>
            <div className="nav-list">{group.items.map((item) => {
              const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
              return <Link key={item.href} href={item.href} onClick={() => setSidebarOpen(false)} className={`nav-item ${active ? "active" : ""}`} title={item.ownerGap ? "The current backend does not support a separate Owner role." : undefined}>
                <item.icon size={16} /><span>{item.label}</span>{item.ownerGap && <span style={{ flex: "none", fontSize: 9, opacity: .8 }}>API GAP</span>}
              </Link>;
            })}</div>
          </div>)}
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-tip"><strong>Smarter decisions</strong>Real-time operations, stock, and sales—all in one place.</div>
          <button type="button" className="nav-item" style={{ background: "transparent", border: 0, cursor: "pointer", width: "100%" }} onClick={onLogout}><LogOut size={16} /><span>Sign out</span></button>
        </div>
      </aside>
      <div className="main-panel">
        <header className="topbar">
          <button className="icon-button mobile-menu" aria-label="Open menu" onClick={() => setSidebarOpen(true)}><Menu size={19} /></button>
          <div className="search-box" style={{ position: "relative" }}><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search products, invoices, customers…" aria-label="Global search" />{query && <button className="icon-button" aria-label="Clear search" onClick={() => setQuery("")}><X size={14} /></button>}
            {(searchResults || searchError) && <div className="search-dropdown">
              {searchError && <div className="search-empty">{searchError}</div>}
              {searchResults?.length === 0 && <div className="search-empty">No matches found.</div>}
              {searchResults?.map((row, index) => {
                const kind = String(row._kind ?? "products");
                const title = String(row.name ?? row.invoiceNumber ?? row.sku ?? "Record");
                const href = kind === "invoices" ? `/invoices` : `/${kind}`;
                return <button key={`${kind}-${String(row.id ?? index)}`} className="search-result" onClick={() => { router.push(href); setQuery(""); }}>
                  <span className="search-kind">{kind.slice(0, -1)}</span><span>{title}</span>
                </button>;
              })}
            </div>}
          </div>
          <div className="top-actions">
            <button type="button" className="icon-button" aria-label="Toggle dark mode" onClick={() => setDark((value) => !value)}><Moon size={18} /></button>
            <Link href="/notifications" className="icon-button" aria-label="Notifications"><Bell size={18} /></Link>
            <Link href="/profile" className="user-chip" aria-label="View my profile"><span className="avatar">{user?.username.slice(0, 2).toUpperCase() ?? "ST"}</span><div className="user-details"><div className="user-name">{user?.username}</div><div className="user-role">{roleTitle(user?.role)}</div></div><ChevronDown size={14} color="#8993a6" /></Link>
          </div>
        </header>
        {children}
        {logoutError && <div className="toast" role="alert">{logoutError}<button className="toast-close" onClick={() => setLogoutError("")} aria-label="Dismiss error"><X size={14} /></button></div>}
      </div>
    </div>
  );
}

function roleTitle(role?: Role) {
  return role?.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase()) ?? "Staff";
}
