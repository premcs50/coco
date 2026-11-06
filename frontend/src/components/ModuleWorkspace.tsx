"use client";

import {
  AlertTriangle, Check, ChevronLeft, ChevronRight, CirclePlus,
  Download, Eye, FileDown, LoaderCircle, Pencil, Plus, Printer, RefreshCw,
  Search, ShieldAlert, X,
} from "lucide-react";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import Link from "next/link";
import { api, apiError, downloadFile } from "@/lib/api";
import { dateTime, displayValue, money } from "@/lib/format";
import { useAuth } from "@/context/AuthContext";
import type { ApiRecord, Role } from "@/types/api";

type Field = {
  name: string;
  label: string;
  kind?: "text" | "number" | "email" | "password" | "select" | "date" | "textarea";
  required?: boolean;
  options?: string[];
  help?: string;
};
type Spec = {
  title: string;
  subtitle: string;
  endpoint?: string;
  fields?: Field[];
  createPath?: string;
  updatePath?: (id: string | number) => string;
  createRoles?: Role[];
  editRoles?: Role[];
  empty?: string;
};

const specs: Record<string, Spec> = {
  products: {
    title: "Product catalog", subtitle: "Manage items, prices, categories, and available stock.",
    endpoint: "/products",
    fields: [
      { name: "sku", label: "SKU", required: true }, { name: "name", label: "Product name", required: true },
      { name: "categoryId", label: "Category ID", kind: "number" }, { name: "supplierId", label: "Supplier ID", kind: "number" },
      { name: "sellingPrice", label: "Selling price", kind: "number", required: true },
      { name: "costPrice", label: "Cost price", kind: "number", required: true },
      { name: "stockQuantity", label: "Opening/current stock", kind: "number", required: true },
      { name: "reorderLevel", label: "Reorder level", kind: "number", required: true },
      { name: "imageUrl", label: "Image URL", help: "The backend accepts an HTTP(S) URL, not a file upload." },
    ],
    createPath: "/products", updatePath: (id) => `/products/${id}`,
    createRoles: ["ADMIN", "STORE_MANAGER", "INVENTORY_STAFF"], editRoles: ["ADMIN", "STORE_MANAGER", "INVENTORY_STAFF"],
  },
  categories: {
    title: "Categories", subtitle: "Keep the catalog organized for faster billing and reporting.",
    endpoint: "/categories", fields: [{ name: "name", label: "Category name", required: true }],
    createPath: "/categories", updatePath: (id) => `/categories/${id}`,
    createRoles: ["ADMIN", "STORE_MANAGER", "INVENTORY_STAFF"], editRoles: ["ADMIN", "STORE_MANAGER", "INVENTORY_STAFF"],
  },
  customers: {
    title: "Customers", subtitle: "Customer records and linked purchase history.",
    endpoint: "/customers", fields: [
      { name: "name", label: "Full name", required: true }, { name: "phone", label: "Phone", required: true },
      { name: "email", label: "Email", kind: "email" },
    ], createPath: "/customers", updatePath: (id) => `/customers/${id}`,
    createRoles: ["ADMIN", "STORE_MANAGER", "BILLING_STAFF"], editRoles: ["ADMIN", "STORE_MANAGER", "BILLING_STAFF"],
  },
  suppliers: {
    title: "Suppliers", subtitle: "Supplier directory, preferred partners, and purchasing history.",
    endpoint: "/suppliers", fields: [
      { name: "name", label: "Supplier name", required: true }, { name: "contact", label: "Contact", required: true },
      { name: "email", label: "Email", kind: "email" }, { name: "preferred", label: "Preferred", kind: "select", options: ["false", "true"] },
    ], createPath: "/suppliers", updatePath: (id) => `/suppliers/${id}`,
    createRoles: ["ADMIN", "STORE_MANAGER", "INVENTORY_STAFF"], editRoles: ["ADMIN", "STORE_MANAGER", "INVENTORY_STAFF"],
  },
};

const dateToday = () => new Date().toISOString().slice(0, 10);
const monthAgo = () => {
  const date = new Date();
  date.setDate(date.getDate() - 30);
  return date.toISOString().slice(0, 10);
};

export function ModuleWorkspace({ module }: { module: string }) {
  const { user, can } = useAuth();
  if (module === "reports" || module === "analytics") return <ReportsWorkspace mode={module} />;
  if (module === "inventory") return <InventoryWorkspace />;
  if (module === "invoices") return <InvoicesWorkspace />;
  if (module === "purchases") return <PurchasesWorkspace />;
  if (module === "shifts") return <ShiftsWorkspace />;
  if (module === "notifications") return <NotificationsWorkspace />;
  if (module === "audit") return <AuditWorkspace />;
  if (module === "settings") return <SettingsWorkspace />;
  if (module === "users") return <UsersWorkspace />;
  if (module === "owners") return <OwnerGap />;
  if (module === "profile") return <ProfileWorkspace />;
  const spec = specs[module];
  if (!spec) return <NotFoundModule module={module} />;
  return <CrudWorkspace module={module} spec={spec} role={user?.role} can={can} />;
}

function CrudWorkspace({ module, spec, role, can }: {
  module: string;
  spec: Spec;
  role?: Role;
  can: (...roles: Role[]) => boolean;
}) {
  const [rows, setRows] = useState<ApiRecord[]>([]);
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [modal, setModal] = useState<ApiRecord | null | "new">(null);
  const [detail, setDetail] = useState<{ title: string; data: unknown } | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const endpoint = spec.endpoint ?? "";

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await api.get<ApiRecord[]>(endpoint);
      setRows(Array.isArray(data) ? data : []);
      setError("");
    } catch (requestError) { setError(apiError(requestError).message); }
    finally { setLoading(false); }
  }, [endpoint]);
  useEffect(() => { void load(); }, [load]);

  const filtered = useMemo(() => rows.filter((row) =>
    Object.values(row).some((value) => displayValue(value).toLowerCase().includes(query.toLowerCase()))), [rows, query]);
  const pageSize = 10;
  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const visible = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const fields = spec.fields ?? [];
  const canCreate = Boolean(spec.createRoles && spec.createRoles.includes(role as Role) && can(...spec.createRoles));
  const canEdit = Boolean(spec.editRoles && spec.editRoles.includes(role as Role) && can(...spec.editRoles));
  const onSave = async (values: Record<string, string>) => {
    setBusy(true);
    try {
      const payload = Object.fromEntries(fields.map((field) => {
        const value = values[field.name] ?? "";
        if (!value) return [field.name, field.kind === "select" && field.name === "preferred" ? false : null];
        if (field.kind === "number") return [field.name, Number(value)];
        if (field.kind === "select" && ["true", "false"].includes(value)) return [field.name, value === "true"];
        return [field.name, value];
      }).filter(([, value]) => value !== null));
      if (modal === "new") await api.post(spec.createPath ?? endpoint, payload);
      else if (modal && spec.updatePath) await api.put(spec.updatePath(String(modal.id)), payload);
      setModal(null);
      setNotice(`${spec.title} saved.`);
      await load();
    } catch (requestError) { setError(apiError(requestError).message); }
    finally { setBusy(false); }
  };

  const toggleActive = async (row: ApiRecord) => {
    const active = row.active !== false;
    if (!window.confirm(`${active ? "Deactivate" : "Activate"} ${displayValue(row.name ?? row.username)}?`)) return;
    const root = module === "products" ? "/products" : module === "suppliers" ? "/suppliers" : "/customers";
    try {
      await api.put(`${root}/${row.id}/active`, { active: !active });
      setNotice(`Record ${active ? "deactivated" : "activated"}.`);
      await load();
    } catch (requestError) { setError(apiError(requestError).message); }
  };
  const showCustomerHistory = async (row: ApiRecord) => {
    try {
      const { data } = await api.get(`/customers/${row.id}/history`);
      setDetail({ title: `Purchase history · ${String(row.name)}`, data });
    } catch (requestError) { setError(apiError(requestError).message); }
  };
  const mergeCustomer = async (row: ApiRecord) => {
    const targetId = window.prompt(`Enter the target customer ID to merge ${String(row.name)} into:`);
    if (!targetId || !/^\d+$/.test(targetId)) return;
    if (!window.confirm(`Merge ${String(row.name)} into customer ${targetId}? The source record will be deactivated.`)) return;
    try {
      await api.post(`/customers/${row.id}/merge/${targetId}`);
      setNotice("Customer records merged."); await load();
    } catch (requestError) { setError(apiError(requestError).message); }
  };
  const supplierPerformance = async (row: ApiRecord) => {
    try {
      const { data } = await api.get(`/suppliers/${row.id}/performance`);
      setDetail({ title: `Supplier performance · ${String(row.name)}`, data });
    } catch (requestError) { setError(apiError(requestError).message); }
  };
  const togglePreferred = async (row: ApiRecord) => {
    try {
      await api.put(`/suppliers/${row.id}/preferred`, { preferred: !row.preferred });
      setNotice(`Supplier marked ${row.preferred ? "non-preferred" : "preferred"}.`); await load();
    } catch (requestError) { setError(apiError(requestError).message); }
  };

  const headers = rows.length ? Object.keys(rows[0]).filter((key) => !["passwordHash", "version", "lines", "payments"].includes(key)) : [];

  return <main className="page-content">
    <PageHeader title={spec.title} subtitle={spec.subtitle} eyebrow="MANAGE YOUR STORE" action={canCreate ? <button className="btn btn-primary" onClick={() => setModal("new")}><Plus size={15} /> Add {singular(module)}</button> : undefined} />
    {notice && <Toast message={notice} onDismiss={() => setNotice("")} />}
    {error && <Notice message={error} />}
    <div className="card">
      <div className="card-heading resource-toolbar"><div><h2 className="card-title">All {spec.title.toLowerCase()}</h2><div className="card-subtitle">{filtered.length} records · live from the STOCKLY API</div></div><div className="toolbar"><div className="search-box table-search"><Search size={15} /><input placeholder="Filter records…" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }} /></div><button className="btn btn-secondary" aria-label="Refresh table" onClick={() => void load()}><RefreshCw size={14} /></button></div></div>
      {loading ? <div className="loading"><span className="spinner" />Loading records…</div> : visible.length === 0 ? <div className="empty-state">{error ? "Records could not be loaded." : spec.empty ?? "No records yet. Add one to get started."}</div> : <div className="table-wrap"><table><thead><tr>{headers.map((header) => <th key={header}>{labelFor(header)}</th>)}{(canEdit || ["customers", "products", "suppliers"].includes(module)) && <th>Actions</th>}</tr></thead><tbody>{visible.map((row, index) => <tr key={String(row.id ?? index)}>{headers.map((header) => <td key={header}>{renderCell(header, row[header])}</td>)}{(canEdit || ["products", "customers", "suppliers"].includes(module)) && <td><div className="table-actions">{canEdit && spec.updatePath && <button className="icon-button" title="Edit record" onClick={() => setModal(row)}><Pencil size={14} /></button>}{module !== "categories" && row.active !== undefined && <button className={`btn ${row.active ? "btn-danger" : "btn-secondary"}`} onClick={() => void toggleActive(row)}>{row.active ? "Deactivate" : "Activate"}</button>}{module === "customers" && <><button className="icon-button" title="View purchase history" onClick={() => void showCustomerHistory(row)}><Eye size={14} /></button>{can("ADMIN") && <button className="btn btn-ghost" onClick={() => void mergeCustomer(row)}>Merge</button>}</>}{module === "suppliers" && <><button className="btn btn-ghost" onClick={() => void supplierPerformance(row)}>Performance</button>{can("ADMIN", "STORE_MANAGER") && <button className="btn btn-ghost" onClick={() => void togglePreferred(row)}>{row.preferred ? "Unmark preferred" : "Preferred"}</button>}</>}</div></td>}</tr>)}</tbody></table></div>}
      <div className="table-footer"><span>Page {currentPage} of {pageCount}</span><div className="toolbar"><button className="btn btn-secondary" disabled={currentPage <= 1} onClick={() => setPage(currentPage - 1)}><ChevronLeft size={14} /> Previous</button><button className="btn btn-secondary" disabled={currentPage >= pageCount} onClick={() => setPage(currentPage + 1)}>Next <ChevronRight size={14} /></button></div></div>
    </div>
    {modal && <ResourceModal spec={spec} record={modal === "new" ? null : modal} busy={busy} onClose={() => setModal(null)} onSave={onSave} />}
    {detail && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setDetail(null); }}><section className="modal-card" role="dialog" aria-modal="true"><div className="modal-heading"><h2 className="card-title">{detail.title}</h2><button className="icon-button" aria-label="Close dialog" onClick={() => setDetail(null)}><X size={18} /></button></div><pre className="json-detail">{JSON.stringify(detail.data, null, 2)}</pre></section></div>}
  </main>;
}

function ResourceModal({ spec, record, busy, onClose, onSave }: {
  spec: Spec; record: ApiRecord | null; busy: boolean; onClose: () => void;
  onSave: (values: Record<string, string>) => Promise<void>;
}) {
  const fields = spec.fields ?? [];
  const shape = Object.fromEntries(fields.map((field) => [
    field.name,
    field.required
      ? z.string().min(1, `${field.label} is required`)
      : field.kind === "email" ? z.union([z.literal(""), z.string().email("Enter a valid email")]) : z.string(),
  ]));
  const schema = z.object(shape);
  const initial = Object.fromEntries(fields.map((field) => [field.name, record?.[field.name] == null ? "" : String(record[field.name])]));
  const { register, handleSubmit, formState: { errors } } = useForm<Record<string, string>>({
    resolver: zodResolver(schema), defaultValues: initial,
  });
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><section className="modal-card" role="dialog" aria-modal="true" aria-labelledby="modal-heading">
    <div className="modal-heading"><div><h2 id="modal-heading" className="card-title">{record ? `Edit ${singular(spec.title.toLowerCase())}` : `Add ${singular(spec.title.toLowerCase())}`}</h2><div className="card-subtitle">Changes are saved to the backend.</div></div><button className="icon-button" aria-label="Close dialog" onClick={onClose}><X size={18} /></button></div>
    <form onSubmit={handleSubmit(onSave)}><div className="field-grid modal-fields">{fields.map((field) => <div className="field" key={field.name}><label htmlFor={`field-${field.name}`}>{field.label}{field.required && " *"}</label>{field.kind === "textarea" ? <textarea id={`field-${field.name}`} className="textarea" {...register(field.name)} /> : field.kind === "select" ? <select id={`field-${field.name}`} className="select" {...register(field.name)}>{field.options?.map((option) => <option key={option} value={option}>{option}</option>)}</select> : <input id={`field-${field.name}`} className="input" type={field.kind ?? "text"} step={field.kind === "number" ? "any" : undefined} min={field.kind === "number" ? "0" : undefined} placeholder={field.help ?? ""} {...register(field.name)} />}{field.help && <small className="field-help">{field.help}</small>}{errors[field.name]?.message && <span className="form-error">{String(errors[field.name]?.message)}</span>}</div>)}</div><div className="modal-footer"><button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button><button className="btn btn-primary" disabled={busy} type="submit">{busy ? <LoaderCircle size={15} className="spin" /> : <Check size={15} />}{busy ? "Saving…" : "Save record"}</button></div></form>
  </section></div>;
}

function InventoryWorkspace() {
  const [products, setProducts] = useState<ApiRecord[]>([]);
  const [movements, setMovements] = useState<ApiRecord[]>([]);
  const [suggestions, setSuggestions] = useState<ApiRecord[]>([]);
  const [adjustment, setAdjustment] = useState({ productId: "", quantityChange: "", reason: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const { can } = useAuth();
  const canAdjust = can("ADMIN", "STORE_MANAGER", "INVENTORY_STAFF");
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [stock, history, reorder] = await Promise.all([
        api.get<ApiRecord[]>("/inventory/stock"), api.get<ApiRecord[]>("/inventory/movements"),
        api.get<ApiRecord[]>("/inventory/reorder-suggestions"),
      ]);
      setProducts(stock.data); setMovements(history.data.slice(0, 12)); setSuggestions(reorder.data);
      setError("");
    } catch (requestError) { setError(apiError(requestError).message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const schema = z.object({ productId: z.string().min(1), quantityChange: z.coerce.number().int(), reason: z.string().min(3).max(500) });
    const parsed = schema.safeParse(adjustment);
    if (!parsed.success) { setError(parsed.error.issues[0]?.message ?? "Complete all fields."); return; }
    try {
      await api.post("/inventory/adjustments", { productId: Number(parsed.data.productId), quantityChange: parsed.data.quantityChange, reason: parsed.data.reason });
      setAdjustment({ productId: "", quantityChange: "", reason: "" }); setError(""); await load();
    } catch (requestError) { setError(apiError(requestError).message); }
  };
  const countStock = async (product: ApiRecord) => {
    const raw = window.prompt(`Enter the counted quantity for ${String(product.name)}:`, String(product.stockQuantity));
    if (raw === null) return;
    const countedQuantity = Number(raw);
    if (!Number.isInteger(countedQuantity) || countedQuantity < 0) { setError("Enter a whole number greater than or equal to zero."); return; }
    try { await api.post("/inventory/counts", [{ productId: Number(product.id), countedQuantity }]); await load(); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  return <main className="page-content"><PageHeader title="Inventory control" subtitle="Live stock levels, movement history, and reorder guidance." eyebrow="STOCK OPERATIONS" action={<button className="btn btn-secondary" onClick={() => void load()}><RefreshCw size={14} /> Refresh</button>} />
    {error && <Notice message={error} />}
    <div className="dashboard-grid">
      <section className="card"><div className="card-heading"><div><h2 className="card-title">Current stock</h2><div className="card-subtitle">Current product stock and reorder points</div></div></div>{loading ? <div className="loading"><span className="spinner" />Loading stock…</div> : <div className="table-wrap"><table><thead><tr><th>Product</th><th>On hand</th><th>Reorder point</th>{canAdjust && <th>Actions</th>}</tr></thead><tbody>{products.map((row) => <tr key={String(row.id)}><td><span className="table-primary">{displayValue(row.name)}</span><div className="table-secondary">{displayValue(row.sku)}</div></td><td>{displayValue(row.stockQuantity)}</td><td>{displayValue(row.reorderLevel)}</td>{canAdjust && <td><button className="btn btn-secondary" onClick={() => void countStock(row)}>Stock count</button></td>}</tr>)}</tbody></table></div>}</section>
      {canAdjust && <section className="card"><div className="card-heading"><div><h2 className="card-title">Adjust stock</h2><div className="card-subtitle">Adjustment requires an audit reason.</div></div></div><form onSubmit={submit} className="stack-form"><div className="field"><label htmlFor="adjust-product">Product</label><select className="select" id="adjust-product" value={adjustment.productId} onChange={(e) => setAdjustment({ ...adjustment, productId: e.target.value })}><option value="">Select a product</option>{products.map((item) => <option key={String(item.id)} value={String(item.id)}>{String(item.sku)} · {String(item.name)}</option>)}</select></div><div className="field"><label htmlFor="adjust-quantity">Quantity change (+ or −)</label><input id="adjust-quantity" className="input" type="number" step="1" value={adjustment.quantityChange} onChange={(e) => setAdjustment({ ...adjustment, quantityChange: e.target.value })} /></div><div className="field"><label htmlFor="adjust-reason">Reason</label><textarea id="adjust-reason" className="textarea" maxLength={500} value={adjustment.reason} onChange={(e) => setAdjustment({ ...adjustment, reason: e.target.value })} /></div><button className="btn btn-primary" type="submit"><CirclePlus size={15} /> Save adjustment</button></form></section>}
    </div>
    <div className="dashboard-grid" style={{ marginTop: 16 }}>
      <section className="card"><div className="card-heading"><div><h2 className="card-title">Reorder suggestions</h2><div className="card-subtitle">Based on stock, reorder levels, and the last 30 days of sales.</div></div></div>{suggestions.length ? <div className="table-wrap"><table><thead><tr><th>Product</th><th>Available</th><th>Recent units</th><th>Suggested</th><th>Supplier</th></tr></thead><tbody>{suggestions.map((row, index) => <tr key={String(row.productId ?? index)}><td>{displayValue(row.name)}</td><td><span className="badge badge-red">{displayValue(row.currentStock)}</span></td><td>{displayValue(row.soldLast30Days)}</td><td>{displayValue(row.suggestedQuantity)}</td><td>{displayValue(row.preferredSupplier)}</td></tr>)}</tbody></table></div> : <div className="empty-state">No reorder suggestions right now.</div>}</section>
      <section className="card"><div className="card-heading"><div><h2 className="card-title">Recent stock movement</h2><div className="card-subtitle">Audited stock changes</div></div></div>{movements.length ? <div className="simple-list">{movements.map((row, index) => <div className="simple-row" key={String(row.id ?? index)}><span className="row-name">{displayValue(row.movementType)}<small>{displayValue(row.reason)} · {displayValue(row.actor)}</small></span><span className={Number(row.quantityChange) < 0 ? "stock-count" : "positive-count"}>{Number(row.quantityChange) > 0 ? "+" : ""}{displayValue(row.quantityChange)}</span></div>)}</div> : <div className="empty-state">No stock movements recorded.</div>}</section>
    </div>
  </main>;
}

function InvoicesWorkspace() {
  const [rows, setRows] = useState<ApiRecord[]>([]);
  const [products, setProducts] = useState<ApiRecord[]>([]);
  const [customers, setCustomers] = useState<ApiRecord[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ productId: "", quantity: "1", customerId: "", discount: "0", paymentMode: "CASH", amount: "", secondPaymentMode: "CARD", secondAmount: "", thirdPaymentMode: "UPI", thirdAmount: "", tillId: "MAIN" });
  const { can } = useAuth();
  const writable = can("ADMIN", "STORE_MANAGER", "BILLING_STAFF");
  const load = useCallback(async () => {
    try {
      const [invoiceResponse, productResponse, customerResponse] = await Promise.all([
        api.get<ApiRecord[]>("/invoices"), api.get<ApiRecord[]>("/products"),
        api.get<ApiRecord[]>("/customers"),
      ]);
      setRows(invoiceResponse.data.slice().reverse());
      setProducts(productResponse.data.filter((item) => item.active !== false));
      setCustomers(customerResponse.data.filter((item) => item.active !== false));
    } catch (requestError) { setError(apiError(requestError).message); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  const selectedProduct = products.find((item) => String(item.id) === form.productId);
  const subtotal = Number(selectedProduct?.sellingPrice ?? 0) * Number(form.quantity || 0);
  const total = Math.max(0, subtotal - Number(form.discount || 0));
  const createInvoice = async (hold: boolean) => {
    const schema = z.object({
      productId: z.string().min(1, "Choose a product."),
      quantity: z.coerce.number().int().min(1),
      discount: z.coerce.number().min(0),
      amount: hold ? z.string() : z.coerce.number().positive(),
      secondAmount: z.string(),
      thirdAmount: z.string(),
      tillId: z.string().min(1).max(40),
    });
    const parsed = schema.safeParse(form);
    if (!parsed.success) { setError(parsed.error.issues[0]?.message ?? "Check the invoice details."); return; }
    const paymentItems = [
      { mode: form.paymentMode, amount: Number(form.amount) },
      { mode: form.secondPaymentMode, amount: Number(form.secondAmount) },
      { mode: form.thirdPaymentMode, amount: Number(form.thirdAmount) },
    ].filter((payment) => payment.amount > 0);
    if (!hold && Math.abs(paymentItems.reduce((sum, payment) => sum + payment.amount, 0) - total) > 0.001) {
      setError(`Payment splits must equal the invoice total (${money(total)}).`); return;
    }
    setBusy(true);
    try {
      const payload = {
        customerId: form.customerId ? Number(form.customerId) : null,
        tillId: form.tillId,
        lines: [{ productId: Number(form.productId), quantity: Number(form.quantity) }],
        discount: Number(form.discount),
        payments: hold ? [] : paymentItems,
      };
      await api.post(hold ? "/invoices/hold" : "/invoices", payload);
      setNotice(hold ? "Invoice saved to held sales." : "Sale completed and stock updated.");
      setForm({ productId: "", quantity: "1", customerId: "", discount: "0", paymentMode: "CASH", amount: "", secondPaymentMode: "CARD", secondAmount: "", thirdPaymentMode: "UPI", thirdAmount: "", tillId: "MAIN" });
      setError(""); await load();
    } catch (requestError) { setError(apiError(requestError).message); }
    finally { setBusy(false); }
  };
  const mutateInvoice = async (row: ApiRecord, action: "void" | "return" | "recall") => {
    try {
      if (action === "void") {
        const reason = window.prompt("Enter the reason for voiding this invoice:");
        if (!reason) return;
        if (!window.confirm(`Void invoice ${String(row.invoiceNumber)}? Stock will be restored.`)) return;
        await api.post(`/invoices/${row.id}/void`, { reason });
      } else if (action === "return") {
        const line = Array.isArray(row.lines) ? row.lines[0] as ApiRecord : null;
        if (!line) { setError("There are no invoice lines available to return."); return; }
        const count = Number(window.prompt("Quantity to return:", "1"));
        if (!Number.isInteger(count) || count < 1) return;
        const reason = window.prompt("Return reason:");
        if (!reason) return;
        await api.post(`/invoices/${row.id}/returns`, { lines: [{ productId: line.productId, quantity: count }], refundMode: "CASH", reason });
      } else {
        await api.post(`/invoices/${row.id}/recall`);
      }
      setNotice("Invoice updated."); setError(""); await load();
    } catch (requestError) { setError(apiError(requestError).message); }
  };
  const printInvoice = async (id: unknown) => {
    try {
      const { data } = await api.get<Blob>(`/invoices/${String(id)}/print`, { responseType: "blob" });
      const url = URL.createObjectURL(data); window.open(url, "_blank", "noopener,noreferrer"); window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (requestError) { setError(apiError(requestError).message); }
  };
  return <main className="page-content"><PageHeader title="Billing & invoices" subtitle="Create a sale, hold an incomplete bill, and manage invoice history." eyebrow="CHECKOUT" action={<button className="btn btn-secondary" onClick={() => void load()}><RefreshCw size={14} /> Refresh</button>} />
    {error && <Notice message={error} />}{notice && <Toast message={notice} onDismiss={() => setNotice("")} />}
    {writable && <section className="card invoice-builder"><div className="card-heading"><div><h2 className="card-title">New invoice</h2><div className="card-subtitle">Stock availability and prices are verified by the backend.</div></div></div>
      <div className="invoice-form-grid">
        <div className="field"><label htmlFor="invoice-product">Product</label><select id="invoice-product" className="select" value={form.productId} onChange={(e) => setForm({ ...form, productId: e.target.value, amount: "" })}><option value="">Select item</option>{products.map((item) => <option key={String(item.id)} value={String(item.id)}>{String(item.name)} · {money(item.sellingPrice)} · {String(item.stockQuantity)} in stock</option>)}</select></div>
        <div className="field"><label htmlFor="invoice-quantity">Quantity</label><input id="invoice-quantity" className="input" type="number" min="1" step="1" value={form.quantity} onChange={(e) => setForm({ ...form, quantity: e.target.value })} /></div>
        <div className="field"><label htmlFor="invoice-customer">Customer (optional)</label><select id="invoice-customer" className="select" value={form.customerId} onChange={(e) => setForm({ ...form, customerId: e.target.value })}><option value="">Walk-in customer</option>{customers.map((item) => <option key={String(item.id)} value={String(item.id)}>{String(item.name)} · {String(item.phone)}</option>)}</select></div>
        <div className="field"><label htmlFor="invoice-discount">Discount</label><input id="invoice-discount" className="input" type="number" min="0" step="0.01" value={form.discount} onChange={(e) => setForm({ ...form, discount: e.target.value })} /></div>
        <div className="field"><label htmlFor="invoice-mode">Payment mode 1</label><select id="invoice-mode" className="select" value={form.paymentMode} onChange={(e) => setForm({ ...form, paymentMode: e.target.value })}>{["CASH", "CARD", "UPI"].map((mode) => <option key={mode}>{mode}</option>)}</select></div>
        <div className="field"><label htmlFor="invoice-amount">Payment amount 1</label><input id="invoice-amount" className="input" type="number" min="0" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} placeholder={total.toFixed(2)} /></div>
        <div className="field"><label htmlFor="invoice-mode-2">Payment mode 2 (optional)</label><select id="invoice-mode-2" className="select" value={form.secondPaymentMode} onChange={(e) => setForm({ ...form, secondPaymentMode: e.target.value })}>{["CASH", "CARD", "UPI"].map((mode) => <option key={mode}>{mode}</option>)}</select></div>
        <div className="field"><label htmlFor="invoice-amount-2">Payment amount 2</label><input id="invoice-amount-2" className="input" type="number" min="0" step="0.01" value={form.secondAmount} onChange={(e) => setForm({ ...form, secondAmount: e.target.value })} placeholder="0.00" /></div>
        <div className="field"><label htmlFor="invoice-mode-3">Payment mode 3 (optional)</label><select id="invoice-mode-3" className="select" value={form.thirdPaymentMode} onChange={(e) => setForm({ ...form, thirdPaymentMode: e.target.value })}>{["CASH", "CARD", "UPI"].map((mode) => <option key={mode}>{mode}</option>)}</select></div>
        <div className="field"><label htmlFor="invoice-amount-3">Payment amount 3</label><input id="invoice-amount-3" className="input" type="number" min="0" step="0.01" value={form.thirdAmount} onChange={(e) => setForm({ ...form, thirdAmount: e.target.value })} placeholder="0.00" /></div>
        <div className="field"><label htmlFor="invoice-till">Till ID</label><input id="invoice-till" className="input" maxLength={40} value={form.tillId} onChange={(e) => setForm({ ...form, tillId: e.target.value })} /></div>
        <div className="invoice-total"><span>Estimated invoice total</span><strong>{money(total)}</strong><small>Backend tax rules may change the final total; payment must match the server total.</small></div>
      </div>
      <div className="modal-footer invoice-actions"><button className="btn btn-secondary" disabled={busy} onClick={() => void createInvoice(true)}><FileDown size={15} /> Hold invoice</button><button className="btn btn-primary" disabled={busy} onClick={() => void createInvoice(false)}><CirclePlus size={15} /> Complete sale</button></div>
    </section>}
    <section className="card" style={{ marginTop: 17 }}><div className="card-heading"><div><h2 className="card-title">Invoice history</h2><div className="card-subtitle">Completed, held, returned, and voided bills</div></div></div>{rows.length ? <div className="table-wrap"><table><thead><tr><th>Invoice</th><th>Cashier</th><th>Customer</th><th>Date</th><th>Status</th><th>Total</th><th>Actions</th></tr></thead><tbody>{rows.map((row, index) => <tr key={String(row.id ?? index)}><td className="table-primary">{displayValue(row.invoiceNumber)}</td><td>{displayValue(row.cashier)}</td><td>{displayValue(row.customerId ?? "Walk-in")}</td><td>{dateTime(row.createdAt)}</td><td><StatusBadge value={row.status} /></td><td className="table-primary">{money(row.total)}</td><td><div className="table-actions"><button className="icon-button" title="Print or download PDF" onClick={() => void printInvoice(row.id)}><Printer size={14} /></button>{row.status === "HELD" && <button className="btn btn-secondary" onClick={() => void mutateInvoice(row, "recall")}>Recall</button>}{row.status === "COMPLETED" && <><button className="btn btn-secondary" onClick={() => void mutateInvoice(row, "return")}>Return</button><button className="btn btn-danger" onClick={() => void mutateInvoice(row, "void")}>Void</button></>}</div></td></tr>)}</tbody></table></div> : <div className="empty-state">No invoices have been created.</div>}</section>
  </main>;
}

function PurchasesWorkspace() {
  const [rows, setRows] = useState<ApiRecord[]>([]);
  const [suppliers, setSuppliers] = useState<ApiRecord[]>([]);
  const [products, setProducts] = useState<ApiRecord[]>([]);
  const [form, setForm] = useState({ supplierId: "", productId: "", quantity: "1", unitCost: "" });
  const [error, setError] = useState("");
  const { can } = useAuth();
  const canCreate = can("ADMIN", "STORE_MANAGER", "INVENTORY_STAFF");
  const load = useCallback(async () => {
    try {
      const [orders, supplierResponse, productResponse] = await Promise.all([
        api.get<ApiRecord[]>("/purchases"), api.get<ApiRecord[]>("/suppliers"), api.get<ApiRecord[]>("/products"),
      ]);
      setRows(orders.data.slice().reverse()); setSuppliers(supplierResponse.data); setProducts(productResponse.data);
    } catch (requestError) { setError(apiError(requestError).message); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  const create = async (event: React.FormEvent) => {
    event.preventDefault();
    const schema = z.object({ supplierId: z.string().min(1), productId: z.string().min(1), quantity: z.coerce.number().int().min(1), unitCost: z.coerce.number().min(0) });
    const parsed = schema.safeParse(form);
    if (!parsed.success) { setError(parsed.error.issues[0]?.message ?? "Complete purchase details."); return; }
    try {
      await api.post("/purchases", { supplierId: Number(form.supplierId), lines: [{ productId: Number(form.productId), quantity: parsed.data.quantity, unitCost: parsed.data.unitCost }] });
      setForm({ supplierId: "", productId: "", quantity: "1", unitCost: "" }); setError(""); await load();
    } catch (requestError) { setError(apiError(requestError).message); }
  };
  const action = async (row: ApiRecord, type: "approve" | "receive" | "return") => {
    try {
      if (type === "approve") await api.post(`/purchases/${row.id}/approve`);
      else {
        const line = Array.isArray(row.lines) ? row.lines[0] as ApiRecord : null;
        if (!line) { setError("Purchase order has no lines."); return; }
        const qty = Number(window.prompt(`${type === "receive" ? "Quantity received" : "Quantity returned"} for product ${String(line.productId)}:`, "1"));
        if (!Number.isInteger(qty) || qty < 1) return;
        await api.post(`/purchases/${row.id}/${type === "receive" ? "receive" : "returns"}`, [{ productId: line.productId, quantity: qty }]);
      }
      setError(""); await load();
    } catch (requestError) { setError(apiError(requestError).message); }
  };
  return <main className="page-content"><PageHeader title="Purchasing" subtitle="Track supplier orders, approvals, receipts, and returns." eyebrow="SUPPLY CHAIN" action={<button className="btn btn-secondary" onClick={() => void load()}><RefreshCw size={14} /> Refresh</button>} />{error && <Notice message={error} />}
    {canCreate && <section className="card compact-form"><div className="card-heading"><div><h2 className="card-title">Create purchase order</h2><div className="card-subtitle">Stock changes only when goods are received.</div></div></div><form className="inline-form" onSubmit={create}><div className="field"><label>Supplier</label><select className="select" value={form.supplierId} onChange={(e) => setForm({ ...form, supplierId: e.target.value })}><option value="">Choose supplier</option>{suppliers.map((item) => <option key={String(item.id)} value={String(item.id)}>{String(item.name)}</option>)}</select></div><div className="field"><label>Product</label><select className="select" value={form.productId} onChange={(e) => setForm({ ...form, productId: e.target.value })}><option value="">Choose product</option>{products.map((item) => <option key={String(item.id)} value={String(item.id)}>{String(item.sku)} · {String(item.name)}</option>)}</select></div><div className="field"><label>Quantity</label><input className="input" type="number" min="1" step="1" value={form.quantity} onChange={(e) => setForm({ ...form, quantity: e.target.value })} /></div><div className="field"><label>Unit cost</label><input className="input" type="number" min="0" step="0.01" value={form.unitCost} onChange={(e) => setForm({ ...form, unitCost: e.target.value })} /></div><button className="btn btn-primary" type="submit"><Plus size={14} /> Create order</button></form></section>}
    <section className="card" style={{ marginTop: 17 }}><div className="card-heading"><div><h2 className="card-title">Purchase orders</h2><div className="card-subtitle">{rows.length} records</div></div></div>{rows.length ? <div className="table-wrap"><table><thead><tr><th>Order</th><th>Supplier</th><th>Created</th><th>Items</th><th>Total</th><th>Status</th><th>Actions</th></tr></thead><tbody>{rows.map((row, index) => <tr key={String(row.id ?? index)}><td>PO-{displayValue(row.id)}</td><td>{displayValue(row.supplierId)}</td><td>{dateTime(row.createdAt)}</td><td>{Array.isArray(row.lines) ? row.lines.length : 0}</td><td>{money(row.total)}</td><td><StatusBadge value={row.status} /></td><td><div className="table-actions">{row.status === "PENDING_APPROVAL" && can("ADMIN", "STORE_MANAGER") && <button className="btn btn-secondary" onClick={() => void action(row, "approve")}>Approve</button>}{["APPROVED", "PARTIALLY_RECEIVED"].includes(String(row.status)) && <button className="btn btn-secondary" onClick={() => void action(row, "receive")}>Receive</button>}{["RECEIVED", "PARTIALLY_RECEIVED"].includes(String(row.status)) && can("ADMIN", "STORE_MANAGER", "INVENTORY_STAFF") && <button className="btn btn-ghost" onClick={() => void action(row, "return")}>Return</button>}</div></td></tr>)}</tbody></table></div> : <div className="empty-state">No purchase orders recorded.</div>}</section>
  </main>;
}

function ShiftsWorkspace() {
  const [rows, setRows] = useState<ApiRecord[]>([]);
  const [current, setCurrent] = useState<ApiRecord | null>(null);
  const [error, setError] = useState("");
  const [tillId, setTillId] = useState("MAIN");
  const [cash, setCash] = useState("0");
  const [expense, setExpense] = useState({ amount: "", paymentMode: "CASH", description: "" });
  const { user } = useAuth();
  const load = useCallback(async () => {
    try {
      const [history, openShift] = await Promise.all([
        api.get<ApiRecord[]>("/shifts"), api.get<ApiRecord>("/shifts/current").catch(() => ({ data: null })),
      ]);
      setRows(history.data.slice().reverse()); setCurrent(openShift.data);
    } catch (requestError) { setError(apiError(requestError).message); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  const open = async (event: React.FormEvent) => {
    event.preventDefault();
    try { await api.post("/shifts", { tillId, openingCash: Number(cash) }); setError(""); await load(); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  const close = async () => {
    const closingCash = Number(window.prompt("Enter the counted cash at close:", String(current?.openingCash ?? 0)));
    if (!Number.isFinite(closingCash) || closingCash < 0 || !current?.id) return;
    try { const { data } = await api.post(`/shifts/${current.id}/close`, { closingCash }); window.alert(`Shift closed. Cash variance: ${String((data.reconciliation as ApiRecord)?.variance ?? "—")}`); await load(); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  const addExpense = async (event: React.FormEvent) => {
    event.preventDefault();
    const parsed = z.object({ amount: z.coerce.number().positive(), paymentMode: z.enum(["CASH", "CARD", "UPI"]), description: z.string().min(1).max(500) }).safeParse(expense);
    if (!parsed.success) { setError(parsed.error.issues[0]?.message ?? "Enter a valid expense."); return; }
    try { await api.post("/expenses", { ...expense, amount: parsed.data.amount, tillId: current?.tillId ?? tillId }); setExpense({ amount: "", paymentMode: "CASH", description: "" }); setError(""); await load(); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  return <main className="page-content"><PageHeader title="Shifts & cash" subtitle="Open a till, record expenses, and reconcile cash at handover." eyebrow="END OF DAY" />{error && <Notice message={error} />}
    <div className="dashboard-grid">
      <section className="card"><div className="card-heading"><div><h2 className="card-title">{current ? "Current shift" : "Open a shift"}</h2><div className="card-subtitle">{current ? `Active till ${String(current.tillId)} · opened ${dateTime(current.openedAt)}` : "Start cash tracking before serving customers."}</div></div></div>
        {current ? <div className="shift-live"><div className="shift-stat"><span>Opening cash</span><strong>{money(current.openingCash)}</strong></div><div className="shift-stat"><span>Till</span><strong>{displayValue(current.tillId)}</strong></div><button className="btn btn-primary" onClick={() => void close()}>Close shift &amp; reconcile</button></div> : <form onSubmit={open} className="stack-form"><div className="field"><label>Till ID</label><input className="input" value={tillId} maxLength={40} onChange={(e) => setTillId(e.target.value)} /></div><div className="field"><label>Opening cash</label><input className="input" type="number" min="0" step="0.01" value={cash} onChange={(e) => setCash(e.target.value)} /></div><button className="btn btn-primary" type="submit"><CirclePlus size={15} /> Open shift</button></form>}
      </section>
      <section className="card"><div className="card-heading"><div><h2 className="card-title">Record store expense</h2><div className="card-subtitle">Manager-controlled petty cash and expenses.</div></div></div><form onSubmit={addExpense} className="stack-form"><div className="field"><label>Amount</label><input className="input" type="number" min="0.01" step="0.01" value={expense.amount} onChange={(e) => setExpense({ ...expense, amount: e.target.value })} /></div><div className="field"><label>Payment mode</label><select className="select" value={expense.paymentMode} onChange={(e) => setExpense({ ...expense, paymentMode: e.target.value })}>{["CASH", "CARD", "UPI"].map((value) => <option key={value}>{value}</option>)}</select></div><div className="field"><label>Description</label><input className="input" maxLength={500} value={expense.description} onChange={(e) => setExpense({ ...expense, description: e.target.value })} /></div><button className="btn btn-secondary" type="submit">Save expense</button></form></section>
    </div>
    <section className="card" style={{ marginTop: 16 }}><div className="card-heading"><div><h2 className="card-title">Shift history</h2><div className="card-subtitle">User: {user?.username}</div></div></div>{rows.length ? <div className="table-wrap"><table><thead><tr><th>Till</th><th>Opened</th><th>Closed</th><th>Opening cash</th><th>Closing cash</th><th>Status</th></tr></thead><tbody>{rows.map((row, index) => <tr key={String(row.id ?? index)}><td>{displayValue(row.tillId)}</td><td>{dateTime(row.openedAt)}</td><td>{dateTime(row.closedAt)}</td><td>{money(row.openingCash)}</td><td>{money(row.closingCash)}</td><td><StatusBadge value={row.closedAt ? "CLOSED" : "OPEN"} /></td></tr>)}</tbody></table></div> : <div className="empty-state">No shifts recorded.</div>}</section>
  </main>;
}

function NotificationsWorkspace() {
  const [rows, setRows] = useState<ApiRecord[]>([]);
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    try { const { data } = await api.get<ApiRecord[]>("/notifications"); setRows(data); }
    catch (requestError) { setError(apiError(requestError).message); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  const markRead = async (row: ApiRecord) => {
    try { await api.put(`/notifications/${row.id}/read`); await load(); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  return <main className="page-content"><PageHeader title="Notifications" subtitle="Low-stock alerts and important store events for your account." eyebrow="YOUR INBOX" action={<button className="btn btn-secondary" onClick={() => void load()}><RefreshCw size={14} /> Refresh</button>} />{error && <Notice message={error} />}<div className="notification-list">{rows.length ? rows.map((row, index) => <article className={`notification-card ${row.read ? "read" : ""}`} key={String(row.id ?? index)}><span className="notification-icon"><AlertTriangle size={18} /></span><div className="notification-copy"><strong>{displayValue(row.title)}</strong><p>{displayValue(row.message)}</p><small>{dateTime(row.createdAt)}</small></div>{!row.read && <button className="btn btn-secondary" onClick={() => void markRead(row)}>Mark read</button>}</article>) : <div className="card empty-state">No notifications to display.</div>}</div></main>;
}

function AuditWorkspace() {
  const [rows, setRows] = useState<ApiRecord[]>([]);
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    try { const { data } = await api.get<ApiRecord[]>("/admin/audit"); setRows(data); setError(""); }
    catch (requestError) { setError(apiError(requestError).message); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  return <main className="page-content"><PageHeader title="Audit trail" subtitle="Critical actions, logins, stock adjustments, invoices, and permission changes." eyebrow="COMPLIANCE" action={<div className="toolbar"><button className="btn btn-secondary" onClick={() => void load()}><RefreshCw size={14} /> Refresh</button><button className="btn btn-primary" onClick={() => downloadFile("/admin/audit/export?format=csv", "stockly-audit.csv").catch((error) => setError(apiError(error).message))}><Download size={14} /> Export CSV</button></div>} />{error && <Notice message={error} />}<div className="card">{rows.length ? <div className="table-wrap"><table><thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Entity</th><th>Details</th></tr></thead><tbody>{rows.map((row, index) => <tr key={String(row.id ?? index)}><td>{dateTime(row.occurredAt)}</td><td>{displayValue(row.actor)}</td><td><StatusBadge value={row.action} /></td><td>{displayValue(row.entityType)} · {displayValue(row.entityId)}</td><td>{displayValue(row.details)}</td></tr>)}</tbody></table></div> : <div className="empty-state">{error ? "Audit data unavailable." : "No audit events have been recorded."}</div>}</div></main>;
}

function ReportsWorkspace({ mode }: { mode: "reports" | "analytics" }) {
  const [from, setFrom] = useState(monthAgo());
  const [to, setTo] = useState(dateToday());
  const [groupBy, setGroupBy] = useState("date");
  const [result, setResult] = useState<unknown>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [endpoint, setEndpoint] = useState(mode === "analytics" ? "/analytics/sales-trends" : "/reports/sales");
  const [period, setPeriod] = useState("day");
  const run = async (path = endpoint) => {
    setLoading(true); setEndpoint(path);
    try {
      if (from > to) throw new Error("The start date must be on or before the end date.");
      const periodDays = Math.max(1, Math.round((new Date(`${to}T00:00:00.000Z`).getTime() - new Date(`${from}T00:00:00.000Z`).getTime()) / 86_400_000) + 1);
      const previousTo = new Date(`${from}T00:00:00.000Z`);
      previousTo.setDate(previousTo.getDate() - 1);
      const previousFrom = new Date(previousTo);
      previousFrom.setDate(previousFrom.getDate() - periodDays + 1);
      const dateString = (date: Date) => date.toISOString().slice(0, 10);
      const params = path === "/analytics/sales-trends" ? { from, to, period }
        : path === "/reports/compare" ? {
          firstFrom: dateString(previousFrom), firstTo: dateString(previousTo), secondFrom: from, secondTo: to,
        }
        : path === "/analytics/profit-margin" || path === "/analytics/performance" ? { from, to }
          : path === "/analytics/peak-hours" || path === "/analytics/abc" || path === "/analytics/dead-stock" ? { from, to }
            : { from, to, groupBy };
      const { data } = await api.get(path, { params });
      setResult(data); setError("");
    } catch (requestError) { setError(apiError(requestError).message); }
    finally { setLoading(false); }
  };
  const reportLinks = mode === "reports"
    ? [{ label: "Sales summary", path: "/reports/sales" }, { label: "Inventory summary", path: "/reports/inventory" }, { label: "Period comparison", path: "/reports/compare" }]
    : [{ label: "Sales trends", path: "/analytics/sales-trends" }, { label: "Staff & category performance", path: "/analytics/performance" }, { label: "Profit margin", path: "/analytics/profit-margin" }, { label: "Peak hours & days", path: "/analytics/peak-hours" }, { label: "ABC analysis", path: "/analytics/abc" }, { label: "Dead stock", path: "/analytics/dead-stock" }];
  const renderResult = () => {
    if (!result) return <div className="empty-state">Choose a report and run it to load live results.</div>;
    if (Array.isArray(result)) return <div className="table-wrap"><table><thead><tr>{Object.keys((result[0] ?? {}) as object).map((key) => <th key={key}>{labelFor(key)}</th>)}</tr></thead><tbody>{(result as ApiRecord[]).map((row, index) => <tr key={String(row.id ?? row.productId ?? index)}>{Object.entries(row).map(([key, value]) => <td key={key}>{displayValue(value)}</td>)}</tr>)}</tbody></table></div>;
    if (typeof result === "object" && result !== null) {
      const obj = result as Record<string, unknown>;
      const groups = obj.groups ?? obj.sales ?? obj.byStaff ?? obj.byCategory;
      if (typeof groups === "object" && groups !== null) return <div className="table-wrap"><table><thead><tr><th>Group</th><th>Value</th></tr></thead><tbody>{Object.entries(groups).map(([key, value]) => <tr key={key}><td>{key}</td><td>{typeof value === "number" && key !== "byStaff" ? money(value) : displayValue(value)}</td></tr>)}</tbody></table><pre className="json-detail">{JSON.stringify(Object.fromEntries(Object.entries(obj).filter(([key]) => key !== "groups" && key !== "sales" && key !== "byStaff" && key !== "byCategory")), null, 2)}</pre></div>;
      return <pre className="json-detail">{JSON.stringify(result, null, 2)}</pre>;
    }
    return <pre className="json-detail">{String(result)}</pre>;
  };
  const exportReport = async (format: "csv" | "pdf") => {
    try {
      await downloadFile(`/reports/${endpoint.endsWith("inventory") ? "inventory" : "sales"}/export`, `stockly-${endpoint.endsWith("inventory") ? "inventory" : "sales"}.${format}`, { params: { from, to, format } });
    } catch (requestError) { setError(apiError(requestError).message); }
  };
  return <main className="page-content"><PageHeader title={mode === "reports" ? "Reports" : "Sales analytics"} subtitle={mode === "reports" ? "Generate sales, inventory, and date-comparison reports from recorded data." : "Explore sales performance, margin, time patterns, and product contribution."} eyebrow={mode === "reports" ? "BUSINESS REPORTS" : "PERFORMANCE"} />
    <section className="card report-controls"><div className="report-tabs">{reportLinks.map((link) => <button key={link.path} className={`report-tab ${endpoint === link.path ? "selected" : ""}`} onClick={() => { setEndpoint(link.path); setResult(null); }}>{link.label}</button>)}</div>
      <div className="inline-form report-filters"><div className="field"><label htmlFor="from-date">From</label><input className="input" id="from-date" type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} /></div><div className="field"><label htmlFor="to-date">To</label><input className="input" id="to-date" type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} /></div>{endpoint === "/reports/sales" && <div className="field"><label>Group by</label><select className="select" value={groupBy} onChange={(e) => setGroupBy(e.target.value)}>{["date", "product", "category", "staff"].map((value) => <option key={value}>{value}</option>)}</select></div>}{endpoint === "/analytics/sales-trends" && <div className="field"><label>Period</label><select className="select" value={period} onChange={(e) => setPeriod(e.target.value)}>{["day", "week", "month"].map((value) => <option key={value}>{value}</option>)}</select></div>}<button className="btn btn-primary" onClick={() => void run()} disabled={loading}>{loading ? <LoaderCircle size={14} className="spin" /> : <FileDown size={14} />} Run report</button></div>
    </section>
    {error && <Notice message={error} />}
    <section className="card report-result"><div className="card-heading"><div><h2 className="card-title">Report results</h2><div className="card-subtitle">Results are requested from the selected backend endpoint.</div></div>{result !== null && mode === "reports" && endpoint !== "/reports/compare" && <div className="toolbar"><button className="btn btn-secondary" onClick={() => void exportReport("csv")}><Download size={14} /> CSV</button><button className="btn btn-secondary" onClick={() => void exportReport("pdf")}><FileDown size={14} /> PDF</button></div>}</div>{renderResult()}</section>
    {mode === "reports" && <section className="card" style={{ marginTop: 16 }}><div className="card-heading"><div><h2 className="card-title">Inventory report &amp; exports</h2><div className="card-subtitle">Product stock and movement summary</div></div><div className="toolbar"><button className="btn btn-secondary" onClick={() => downloadFile("/reports/inventory/export?format=csv", "stockly-inventory.csv").catch((error) => setError(apiError(error).message))}><Download size={14} /> CSV</button><button className="btn btn-secondary" onClick={() => downloadFile("/reports/inventory/export?format=pdf", "stockly-inventory.pdf").catch((error) => setError(apiError(error).message))}><FileDown size={14} /> PDF</button></div></div><button className="btn btn-secondary" style={{ margin: "0 20px 20px" }} onClick={() => void run("/reports/inventory")}>Load inventory report</button></section>}
    {mode === "analytics" && <SalesTargetWidget date={to} />}
  </main>;
}

function SalesTargetWidget({ date }: { date: string }) {
  const [amount, setAmount] = useState("");
  const [result, setResult] = useState<ApiRecord | null>(null);
  const [error, setError] = useState("");
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    const parsed = z.object({ amount: z.coerce.number().min(0) }).safeParse({ amount });
    if (!parsed.success) { setError("Enter a non-negative sales target."); return; }
    try { await api.post("/analytics/targets", { date, amount: parsed.data.amount }); setError(""); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  const load = async () => {
    try { const { data } = await api.get<ApiRecord>("/analytics/targets", { params: { date } }); setResult(data); setAmount(String(data.target ?? "")); setError(""); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  return <section className="card target-card"><div className="card-heading"><div><h2 className="card-title">Sales target vs actual</h2><div className="card-subtitle">Set or inspect the target for {date}.</div></div><button className="btn btn-secondary" onClick={() => void load()}>Load actual</button></div><form className="inline-form" onSubmit={save}><div className="field"><label>Target amount</label><input className="input" type="number" min="0" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} /></div><button className="btn btn-primary" type="submit">Save target</button>{result && <div className="target-result"><strong>Actual {money(result.actual)}</strong><span>Variance {money(result.variance)}</span></div>}</form>{error && <div className="notice error" style={{ margin: "0 20px 18px" }}>{error}</div>}</section>;
}

function UsersWorkspace() {
  const [rows, setRows] = useState<ApiRecord[]>([]);
  const [sessions, setSessions] = useState<ApiRecord[]>([]);
  const [error, setError] = useState("");
  const [token, setToken] = useState("");
  const [form, setForm] = useState({ username: "", email: "", password: "", role: "BILLING_STAFF" });
  const load = useCallback(async () => {
    try {
      const [users, activeSessions] = await Promise.all([api.get<ApiRecord[]>("/users"), api.get<ApiRecord[]>("/admin/sessions")]);
      setRows(users.data); setSessions(activeSessions.data); setError("");
    }
    catch (requestError) { setError(apiError(requestError).message); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  const create = async (event: React.FormEvent) => {
    event.preventDefault();
    const schema = z.object({ username: z.string().min(3).max(60), email: z.string().email(), password: z.string().min(10).max(72).regex(/[A-Z]/).regex(/[a-z]/).regex(/\d/).regex(/[^A-Za-z0-9]/), role: z.enum(["ADMIN", "STORE_MANAGER", "INVENTORY_STAFF", "BILLING_STAFF"]) });
    const parsed = schema.safeParse(form);
    if (!parsed.success) { setError("Use a valid email and a 10+ character password with upper/lowercase, number, and symbol."); return; }
    try { await api.post("/users", parsed.data); setForm({ username: "", email: "", password: "", role: "BILLING_STAFF" }); setError(""); await load(); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  const toggle = async (row: ApiRecord) => {
    const active = row.active !== false;
    if (!window.confirm(`${active ? "Deactivate" : "Activate"} ${String(row.username)}?`)) return;
    try { await api.put(`/users/${row.id}/active`, { active: !active }); await load(); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  const issueReset = async (row: ApiRecord) => {
    if (!window.confirm(`Issue a single-use password reset token for ${String(row.username)}?`)) return;
    try { const { data } = await api.post<{ token: string }>("/admin/users/" + row.id + "/password-reset-token"); setToken(data.token); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  const forceLogout = async (row: ApiRecord) => {
    if (!window.confirm(`End ${String(row.username)}’s active session?`)) return;
    const account = rows.find((item) => item.username === row.username);
    if (!account?.id) { setError("Could not match this session to an account."); return; }
    try { await api.post(`/admin/users/${account.id}/force-logout`); await load(); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  return <main className="page-content"><PageHeader title="Staff accounts" subtitle="Register, activate, and support staff accounts. Passwords are never displayed." eyebrow="ACCESS CONTROL" />{error && <Notice message={error} />}{token && <div className="notice warning" style={{ marginBottom: 16 }}><ShieldAlert size={18} /><div><strong>One-time reset token</strong><p>Share securely out of band. It expires in 30 minutes.</p><code>{token}</code><button className="btn btn-ghost" onClick={() => setToken("")}>Dismiss</button></div></div>}
    <section className="card compact-form"><div className="card-heading"><div><h2 className="card-title">Register staff</h2><div className="card-subtitle">New accounts are inactive until authorized activation.</div></div></div><form className="inline-form" onSubmit={create}><div className="field"><label>Username</label><input className="input" required minLength={3} value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} /></div><div className="field"><label>Email</label><input className="input" required type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></div><div className="field"><label>Temporary password</label><input className="input" required type="password" autoComplete="new-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></div><div className="field"><label>Role</label><select className="select" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>{["STORE_MANAGER", "INVENTORY_STAFF", "BILLING_STAFF", "ADMIN"].map((role) => <option key={role}>{role}</option>)}</select></div><button className="btn btn-primary" type="submit"><Plus size={14} /> Register</button></form></section>
    <section className="card" style={{ marginTop: 16 }}><div className="card-heading"><div><h2 className="card-title">Accounts</h2><div className="card-subtitle">{rows.length} accounts · no password hashes are exposed</div></div></div>{rows.length ? <div className="table-wrap"><table><thead><tr><th>User</th><th>Role</th><th>Created</th><th>Last login</th><th>Status</th><th>Actions</th></tr></thead><tbody>{rows.map((row, index) => <tr key={String(row.id ?? index)}><td><span className="table-primary">{displayValue(row.username)}</span><div className="table-secondary">{displayValue(row.email)}</div></td><td>{displayValue(row.role)}</td><td>{dateTime(row.createdAt)}</td><td>{dateTime(row.lastLoginAt)}</td><td><StatusBadge value={row.active ? "ACTIVE" : "INACTIVE"} /></td><td><div className="table-actions"><button className="btn btn-secondary" onClick={() => void toggle(row)}>{row.active ? "Deactivate" : "Activate"}</button><button className="btn btn-ghost" onClick={() => void issueReset(row)}>Reset password</button></div></td></tr>)}</tbody></table></div> : <div className="empty-state">No staff accounts found.</div>}</section>
    <section className="card" style={{ marginTop: 16 }}><div className="card-heading"><div><h2 className="card-title">Active sessions</h2><div className="card-subtitle">Administrator can end an active session immediately.</div></div></div>{sessions.length ? <div className="table-wrap"><table><thead><tr><th>User</th><th>Session</th><th>Last request</th><th>Action</th></tr></thead><tbody>{sessions.map((row, index) => <tr key={String(row.sessionId ?? index)}><td>{displayValue(row.username)}</td><td>{String(row.sessionId ?? "").slice(0, 12)}…</td><td>{dateTime(row.lastRequest)}</td><td><button className="btn btn-danger" onClick={() => void forceLogout(row)}>Force logout</button></td></tr>)}</tbody></table></div> : <div className="empty-state">No registered active sessions found.</div>}</section>
  </main>;
}

function SettingsWorkspace() {
  const [settings, setSettings] = useState<ApiRecord[]>([]);
  const [error, setError] = useState("");
  const [key, setKey] = useState("billing.tax-rate-percent");
  const [value, setValue] = useState("");
  const [modes, setModes] = useState({ cash: true, card: true, upi: true });
  const [preferences, setPreferences] = useState({ "lowStock.enabled": true, "approvals.enabled": true, "events.enabled": true });
  const [storeProfile, setStoreProfile] = useState({ name: "", address: "", phone: "", "invoice-footer": "" });
  const [policy, setPolicy] = useState({ "session-timeout-minutes": "30", "maximum-concurrent-sessions": "1", "login-failure-threshold": "5", "login-lock-minutes": "15" });
  const { user } = useAuth();
  const load = useCallback(async () => {
    try {
      const { data } = await api.get<ApiRecord[]>("/settings");
      const values = new Map(data.map((row) => [String(row.settingKey), String(row.settingValue ?? "")]));
      setSettings(data);
      setModes({
        cash: values.get("payment.cash.enabled") !== "false",
        card: values.get("payment.card.enabled") !== "false",
        upi: values.get("payment.upi.enabled") !== "false",
      });
      setStoreProfile({
        name: values.get("store.name") ?? "",
        address: values.get("store.address") ?? "",
        phone: values.get("store.phone") ?? "",
        "invoice-footer": values.get("store.invoice-footer") ?? "",
      });
      setPolicy({
        "session-timeout-minutes": values.get("security.session-timeout-minutes") ?? "30",
        "maximum-concurrent-sessions": values.get("security.maximum-concurrent-sessions") ?? "1",
        "login-failure-threshold": values.get("security.login-failure-threshold") ?? "5",
        "login-lock-minutes": values.get("security.login-lock-minutes") ?? "15",
      });
      const preferencePrefix = `notifications.${user?.username ?? ""}.`;
      setPreferences({
        "lowStock.enabled": values.get(`${preferencePrefix}lowStock.enabled`) !== "false",
        "approvals.enabled": values.get(`${preferencePrefix}approvals.enabled`) !== "false",
        "events.enabled": values.get(`${preferencePrefix}events.enabled`) !== "false",
      });
      setError("");
    }
    catch (requestError) { setError(apiError(requestError).message); }
  }, [user?.username]);
  useEffect(() => { void load(); }, [load]);
  const setSetting = async (event: React.FormEvent) => {
    event.preventDefault();
    const parsed = z.object({ key: z.string().min(1).max(80).regex(/^[a-zA-Z0-9._-]+$/), value: z.string().min(1).max(2000) }).safeParse({ key, value });
    if (!parsed.success) { setError(parsed.error.issues[0]?.message ?? "Enter a valid setting."); return; }
    try { await api.put(`/settings/${encodeURIComponent(key)}`, { value }); setError(""); setValue(""); await load(); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  const saveModes = async () => {
    try { await api.put("/settings/payment-modes", modes); await load(); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  const savePolicy = async () => {
    try { await api.put("/settings/session-policy", policy); await load(); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  const savePreferences = async () => {
    try { await api.put("/notifications/preferences", preferences); setError(""); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  const saveStoreProfile = async (event: React.FormEvent) => {
    event.preventDefault();
    try { await api.put("/settings/store-profile", storeProfile); await load(); }
    catch (requestError) { setError(apiError(requestError).message); }
  };
  return <main className="page-content"><PageHeader title="Store settings" subtitle="Backend-managed policies, payment methods, store profile, and your notification preferences." eyebrow="CONFIGURATION" />{error && <Notice message={error} />}
    <div className="settings-grid"><section className="card"><div className="card-heading"><div><h2 className="card-title">Billing and store policy</h2><div className="card-subtitle">Tax, discount, negative stock, and purchase approvals use dedicated backend keys.</div></div></div><form className="stack-form" onSubmit={setSetting}><div className="field"><label>Setting</label><select className="select" value={key} onChange={(e) => setKey(e.target.value)}>{["billing.tax-rate-percent", "billing.max-discount-percent", "billing.cashier-max-discount-percent", "inventory.allow-negative-stock", "purchasing.approval-threshold", "store.name", "store.address", "store.phone", "store.invoice-footer"].map((item) => <option key={item}>{item}</option>)}</select></div><div className="field"><label>Value</label><input className="input" value={value} onChange={(e) => setValue(e.target.value)} maxLength={2000} /></div><button type="submit" className="btn btn-primary">Save setting</button></form></section>
      <section className="card"><div className="card-heading"><div><h2 className="card-title">Payment modes</h2><div className="card-subtitle">Enable modes accepted at checkout.</div></div></div><div className="settings-options">{Object.entries(modes).map(([mode, enabled]) => <label className="check-row" key={mode}><span>{mode.toUpperCase()}</span><input type="checkbox" checked={enabled} onChange={(e) => setModes({ ...modes, [mode]: e.target.checked })} /></label>)}<button className="btn btn-primary" onClick={() => void saveModes()}>Save payment modes</button></div></section>
      <section className="card"><div className="card-heading"><div><h2 className="card-title">Session &amp; login policy</h2><div className="card-subtitle">Session timeout and lockout configuration.</div></div></div><div className="settings-options">{Object.entries(policy).map(([name, current]) => <div className="field" key={name}><label>{labelFor(name)}</label><input className="input" type="number" min={name.includes("timeout") || name.includes("sessions") ? "0" : "1"} value={current} onChange={(e) => setPolicy({ ...policy, [name]: e.target.value })} /></div>)}<button className="btn btn-primary" onClick={() => void savePolicy()}>Save session policy</button></div></section>
      <section className="card"><div className="card-heading"><div><h2 className="card-title">Store profile &amp; invoice</h2><div className="card-subtitle">Shown on invoice printouts.</div></div></div><form className="settings-options" onSubmit={saveStoreProfile}>{Object.keys(storeProfile).map((field) => <div className="field" key={field}><label>{labelFor(field)}</label><input className="input" value={storeProfile[field as keyof typeof storeProfile]} onChange={(event) => setStoreProfile({ ...storeProfile, [field]: event.target.value })} maxLength={2000} /></div>)}<button className="btn btn-primary" type="submit">Save store profile</button></form></section>
      <section className="card"><div className="card-heading"><div><h2 className="card-title">Notification preferences</h2><div className="card-subtitle">Per-user alert choices.</div></div></div><div className="settings-options">{Object.entries(preferences).map(([name, enabled]) => <label className="check-row" key={name}><span>{labelFor(name)}</span><input type="checkbox" checked={enabled} onChange={(event) => setPreferences({ ...preferences, [name]: event.target.checked })} /></label>)}<button className="btn btn-secondary" onClick={() => void savePreferences()}>Save preferences</button><p className="field-help">Signed in as {user?.username}</p></div></section></div>
    <section className="card settings-table"><div className="card-heading"><div><h2 className="card-title">Saved configuration</h2><div className="card-subtitle">Values returned by the existing settings API</div></div><button className="btn btn-secondary" onClick={() => void load()}><RefreshCw size={14} /> Refresh</button></div>{settings.length ? <div className="table-wrap"><table><thead><tr><th>Setting key</th><th>Value</th></tr></thead><tbody>{settings.map((row, index) => <tr key={String(row.settingKey ?? index)}><td>{displayValue(row.settingKey)}</td><td>{displayValue(row.settingValue)}</td></tr>)}</tbody></table></div> : <div className="empty-state">No settings stored.</div>}</section>
  </main>;
}

function OwnerGap() {
  return <main className="page-content"><PageHeader title="Owner management" subtitle="Dedicated Owner accounts and governance controls." eyebrow="BACKEND DEPENDENCY" /><div className="card gap-card"><div className="gap-icon"><ShieldAlert size={26} /></div><h2>Owner accounts are not supported by this backend</h2><p>The API currently defines <strong>ADMIN</strong>, <strong>STORE_MANAGER</strong>, <strong>INVENTORY_STAFF</strong>, and <strong>BILLING_STAFF</strong>. It has no Owner role, initial three-owner provisioning, first-login password change, Owner activity view, or last-active-Owner protection. This screen intentionally does not create an Owner using another role.</p><Link className="btn btn-secondary" href="/docs/API_INTEGRATION_GAPS.md" target="_blank">Review API gap documentation</Link></div></main>;
}

function ProfileWorkspace() {
  const [profile, setProfile] = useState<ApiRecord | null>(null);
  const [session, setSession] = useState<ApiRecord | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { Promise.all([api.get("/auth/me"), api.get("/auth/session")]).then(([a, b]) => { setProfile(a.data); setSession(b.data); }).catch((failure) => setError(apiError(failure).message)); }, []);
  return <main className="page-content"><PageHeader title="My profile" subtitle="Your account and current session status." eyebrow="ACCOUNT" />{error && <Notice message={error} />}<div className="dashboard-grid"><section className="card"><div className="card-heading"><h2 className="card-title">Profile details</h2></div><div className="profile-details">{Object.entries(profile ?? {}).map(([key, value]) => <div className="profile-row" key={key}><span>{labelFor(key)}</span><strong>{displayValue(value)}</strong></div>)}</div></section><section className="card"><div className="card-heading"><h2 className="card-title">Session details</h2></div><div className="profile-details">{Object.entries(session ?? {}).map(([key, value]) => <div className="profile-row" key={key}><span>{labelFor(key)}</span><strong>{key === "lastActivity" ? dateTime(value) : displayValue(value)}</strong></div>)}</div></section></div><div className="notice warning" style={{ marginTop: 16 }}>Self-service profile editing and change-password APIs are not implemented by the current backend.</div></main>;
}

function NotFoundModule({ module }: { module: string }) {
  return <main className="page-content"><PageHeader title="Module unavailable" subtitle={`No frontend module is defined for “${module}”.`} eyebrow="NOT FOUND" /><div className="card empty-state">Choose an available section from the navigation.</div></main>;
}

function PageHeader({ title, subtitle, eyebrow, action }: { title: string; subtitle: string; eyebrow: string; action?: React.ReactNode }) {
  return <div className="page-head"><div><div className="eyebrow">{eyebrow}</div><h1 className="page-title">{title}</h1><p className="page-subtitle">{subtitle}</p></div>{action}</div>;
}

function Notice({ message }: { message: string }) {
  return <div className="notice error" style={{ marginBottom: 16 }}><AlertTriangle size={16} />{message}</div>;
}

function Toast({ message, onDismiss }: { message: string; onDismiss: () => void }) {
  useEffect(() => { const timer = window.setTimeout(onDismiss, 4500); return () => window.clearTimeout(timer); }, [onDismiss]);
  return <div className="toast" role="status">{message}</div>;
}

function StatusBadge({ value }: { value: unknown }) {
  const status = String(value ?? "unknown").toUpperCase();
  const style = ["ACTIVE", "COMPLETED", "RECEIVED", "CLOSED"].includes(status) ? "badge-green"
    : ["PENDING_APPROVAL", "HELD", "PARTIALLY_RECEIVED", "OPEN"].includes(status) ? "badge-yellow"
      : ["VOIDED", "INACTIVE", "RETURNED"].includes(status) ? "badge-red" : "badge-blue";
  return <span className={`badge ${style}`}>{status.replaceAll("_", " ")}</span>;
}

function renderCell(key: string, value: unknown) {
  if (key === "active" || key === "preferred" || key === "read") return <StatusBadge value={value ? "ACTIVE" : "INACTIVE"} />;
  if (/price|total|subtotal|discount|tax|amount|cash$/i.test(key)) return money(value);
  if (/At$|Date$/.test(key) && typeof value === "string") return dateTime(value);
  if (key === "status" || key === "role" || key === "movementType") return <StatusBadge value={value} />;
  return displayValue(value);
}

function labelFor(value: string) {
  return value.replace(/([a-z])([A-Z])/g, "$1 $2").replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function singular(value: string) {
  if (value === "categories") return "category";
  if (value.endsWith("ies")) return `${value.slice(0, -3)}y`;
  return value.endsWith("s") ? value.slice(0, -1) : value;
}
