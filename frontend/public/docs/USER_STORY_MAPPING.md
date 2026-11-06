# STOCKLY user-story coverage map

This map follows the 14 epic groupings and API coverage documented in the backend `HELP.md`. The original story-level IDs/titles are not present in the repository, so this is an epic-level mapping rather than an invented story-ID list.

| Epic | Frontend surface | Coverage and notable constraints |
|---|---|---|
| EP01–02 Accounts, roles, login | Login, registration, profile, Staff accounts, Owner management | Session-backed authentication, username/email login, and public sign-up as inactive `BILLING_STAFF` are integrated. Owner provisioning, forced first-login password change, self-service profile editing, and self-service password change need backend support. Public registrations require administrator activation before login. |
| EP03 Catalog | Products and categories | Catalog CRUD/search, activation, category linking, and image URL support. Images are URLs; uploads are not supported. |
| EP04 Inventory | Inventory | Stock visibility, low-stock list, adjustments, counts, movement history, reorder suggestions, and negative-stock setting. |
| EP05 Billing | Billing / invoices | Holds, recall, finalize, print, returns, and void actions. The backend is authoritative for discounts/tax; no quote endpoint exists. |
| EP06 Purchases | Purchasing | Purchase creation, approval, receiving, and returns. |
| EP07 Customers | Customers | Customer search/CRUD, history, activation, and merge actions. |
| EP08 Reports and search | Overview, Reports, global search | Dashboard metrics, sales/inventory reporting, period comparison, CSV/PDF export, and global search. |
| EP09 Suppliers | Suppliers | Supplier CRUD, preferred supplier, product assignment, and performance. |
| EP10 Cash and shifts | Shifts & cash | Shift opening/closing, expenses, and shift history. |
| EP11 Sales analysis | Analytics | Trends, performance, profit margin, peak hours, ABC analysis, dead stock, and targets. |
| EP12 Settings and notifications | Settings, Notifications | Store/payment/session settings and per-user notification choices. Notification preferences are initialized from settings returned for the signed-in account. |
| EP13 Audit | Audit log | Audit events, failed-login audit endpoint availability, and CSV export. |
| EP14 Logout | App shell | Explicit sign-out plus server-managed idle session expiry. Open shifts may prevent ordinary logout unless explicitly handled by the backend flow. |

For backend limitations and required API additions, see [API_INTEGRATION_GAPS.md](./API_INTEGRATION_GAPS.md).
