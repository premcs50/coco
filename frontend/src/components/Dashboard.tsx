"use client";

import { AlertTriangle, ArrowDownRight, ArrowUpRight, Boxes, CircleDollarSign, FileText, Package, RefreshCw, Users } from "lucide-react";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api, apiError } from "@/lib/api";
import { dateTime, displayValue, money } from "@/lib/format";
import type { ApiRecord } from "@/types/api";

type DashboardData = {
  date: string;
  salesTotal: number;
  invoiceCount: number;
  lowStockCount: number;
  topProducts: { product: string; quantity: number }[];
  myShiftSales: number;
};

export function Dashboard() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [invoices, setInvoices] = useState<ApiRecord[]>([]);
  const [stock, setStock] = useState<ApiRecord[]>([]);
  const [productCount, setProductCount] = useState(0);
  const [trend, setTrend] = useState<{ date: string; sales: number }[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(true);

  useEffect(() => {
    const today = new Date();
    const from = new Date(today);
    from.setDate(today.getDate() - 6);
    const iso = (date: Date) => date.toISOString().slice(0, 10);
    Promise.all([
      api.get<DashboardData>("/dashboard"),
      api.get<ApiRecord[]>("/invoices"),
      api.get<ApiRecord[]>("/inventory/stock"),
      api.get<{ sales: Record<string, number> }>("/analytics/sales-trends", {
        params: { from: iso(from), to: iso(today), period: "day" },
      }).catch(() => ({ data: { sales: {} } })),
    ]).then(([dashboard, invoiceResponse, stockResponse, trendResponse]) => {
      setData(dashboard.data);
      setInvoices(invoiceResponse.data.filter((row) => row.status !== "HELD").slice(-6).reverse());
      setProductCount(stockResponse.data.length);
      setStock(stockResponse.data.filter((row) => Number(row.stockQuantity) <= Number(row.reorderLevel)).slice(0, 6));
      setTrend(Object.entries(trendResponse.data.sales).map(([date, sales]) => ({ date: date.slice(5), sales: Number(sales) })));
    }).catch((requestError) => setError(apiError(requestError).message))
      .finally(() => setBusy(false));
  }, []);

  const metricCards = useMemo(() => [
    { title: "Today’s revenue", value: money(data?.salesTotal), note: `${data?.invoiceCount ?? 0} completed invoices`, icon: CircleDollarSign, color: "#eaf7ef", ink: "#27915b" },
    { title: "Completed sales", value: String(data?.invoiceCount ?? "—"), note: "Invoices today", icon: FileText, color: "#edf1ff", ink: "#4667f5" },
    { title: "Products in catalog", value: String(productCount), note: "Active and inactive catalog items", icon: Package, color: "#fff5e5", ink: "#cf8422" },
    { title: "Low-stock alerts", value: String(data?.lowStockCount ?? "—"), note: "At or below reorder level", icon: AlertTriangle, color: "#fff0ef", ink: "#d05b4f" },
  ], [data, productCount]);

  if (busy) return <div className="page-content"><div className="loading"><span className="spinner" />Loading live store data…</div></div>;

  return (
    <main className="page-content">
      <div className="page-head"><div><div className="eyebrow">STORE OVERVIEW · {new Date().toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" })}</div><h1 className="page-title">Good day. Here’s your store.</h1><p className="page-subtitle">A live snapshot of today’s operations and the areas that need your attention.</p></div><button className="btn btn-secondary" onClick={() => window.location.reload()}><RefreshCw size={14} /> Refresh</button></div>
      {error && <div className="notice error" style={{ marginBottom: 16 }}>{error} Check that the backend is running and accessible.</div>}
      <section className="metric-grid">
        {metricCards.map((metric) => <article className="metric-card" key={metric.title}><div className="metric-top"><span>{metric.title}</span><span className="metric-icon" style={{ background: metric.color, color: metric.ink }}><metric.icon size={17} /></span></div><div className="metric-value">{metric.value}</div><div className="metric-note">{metric.note}</div></article>)}
      </section>
      <section className="dashboard-grid">
        <article className="card">
          <div className="card-heading"><div><h2 className="card-title">Sales trend</h2><div className="card-subtitle">Daily completed sales · last 7 days</div></div><Link className="btn btn-ghost" href="/analytics">View analytics <ArrowUpRight size={14} /></Link></div>
          <div className="chart-wrap">{trend.length ? <ResponsiveContainer width="100%" height="100%"><LineChart data={trend} margin={{ left: 3, right: 12, top: 8, bottom: 0 }}><CartesianGrid stroke="#edf0f5" strokeDasharray="4 4" vertical={false} /><XAxis dataKey="date" axisLine={false} tickLine={false} tick={{ fontSize: 10, fill: "#8290a8" }} /><YAxis axisLine={false} tickLine={false} tick={{ fontSize: 10, fill: "#8290a8" }} tickFormatter={(value: number) => value >= 1000 ? `${(value / 1000).toFixed(1)}k` : String(value)} /><Tooltip formatter={(value) => money(value)} contentStyle={{ border: "1px solid #e8ebf2", borderRadius: 10, fontSize: 11 }} /><Line type="monotone" dataKey="sales" stroke="#4667f5" strokeWidth={3} dot={{ fill: "#fff", stroke: "#4667f5", strokeWidth: 2, r: 4 }} activeDot={{ r: 6 }} /></LineChart></ResponsiveContainer> : <div className="empty-state">No completed sales in this date range.</div>}</div>
        </article>
        <article className="card">
          <div className="card-heading"><div><h2 className="card-title">Top products</h2><div className="card-subtitle">Units sold today</div></div><span className="metric-icon"><Boxes size={16} /></span></div>
          <div className="simple-list">{data?.topProducts?.length ? data.topProducts.map((item, index) => <div className="simple-row" key={item.product}><span className="rank">{String(index + 1).padStart(2, "0")}</span><span className="row-name">{item.product}</span><span className="row-amount">{item.quantity} units</span></div>) : <div className="empty-state">No sales data for today yet.</div>}</div>
        </article>
      </section>
      <section className="dashboard-grid" style={{ marginTop: 17 }}>
        <article className="card">
          <div className="card-heading"><div><h2 className="card-title">Recent invoices</h2><div className="card-subtitle">Latest completed transactions</div></div><Link className="btn btn-ghost" href="/invoices">All invoices <ArrowUpRight size={14} /></Link></div>
          <div className="table-wrap">{invoices.length ? <table><thead><tr><th>Invoice</th><th>Cashier</th><th>Status</th><th>Total</th></tr></thead><tbody>{invoices.map((row, index) => <tr key={String(row.id ?? index)}><td><span className="table-primary">{displayValue(row.invoiceNumber)}</span><div className="table-secondary">{dateTime(row.createdAt)}</div></td><td>{displayValue(row.cashier)}</td><td><StatusBadge value={row.status} /></td><td className="table-primary">{money(row.total)}</td></tr>)}</tbody></table> : <div className="empty-state">Invoices will appear here when sales are recorded.</div>}</div>
        </article>
        <article className="card">
          <div className="card-heading"><div><h2 className="card-title">Stock needs attention</h2><div className="card-subtitle">Items at or below reorder point</div></div><Link className="btn btn-ghost" href="/inventory">Inventory <ArrowDownRight size={14} /></Link></div>
          <div className="simple-list">{stock.length ? stock.map((row, index) => <div className="simple-row" key={String(row.id ?? index)}><span className="stock-dot"><Package size={14} /></span><span className="row-name">{displayValue(row.name)}<small>{displayValue(row.sku)}</small></span><span className="stock-count">{displayValue(row.stockQuantity)} left</span></div>) : <div className="empty-state">All active products are above their reorder levels.</div>}</div>
        </article>
      </section>
      <p className="data-footnote"><Users size={13} /> Metrics are served by your STOCKLY API. Current shift sales: <strong>{money(data?.myShiftSales)}</strong></p>
    </main>
  );
}

function StatusBadge({ value }: { value: unknown }) {
  const status = String(value ?? "unknown");
  const style = status === "COMPLETED" ? "badge-green" : status === "HELD" ? "badge-yellow" : status === "VOIDED" ? "badge-red" : "badge-blue";
  return <span className={`badge ${style}`}>{status.replaceAll("_", " ")}</span>;
}
