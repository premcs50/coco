# STOCKLY API integration gaps

The frontend uses the Spring API at `/api`. It does not imitate capabilities that the backend does not provide.

## Gaps affecting user-story completion

| Capability | Current backend behavior | Needed to complete the experience |
|---|---|---|
| Dedicated Owner accounts | Roles are `ADMIN`, `STORE_MANAGER`, `INVENTORY_STAFF`, and `BILLING_STAFF`. Bootstrap can create one initial administrator, not three Owners. | Add an `OWNER` role and role policies; provision the required initial Owner accounts safely; prevent deactivation of the last active Owner; expose Owner activity. |
| First-login password change | Staff accounts can be registered inactive and administrators can activate them. There is no forced password-change flag or endpoint. | Add a temporary credential lifecycle, a required-change flag, and an authenticated password-change endpoint. |
| Self-service account edits | `/api/auth/me` and `/api/auth/session` provide read-only profile/session data. | Add validated self-service profile and change-password endpoints. |
| Public password recovery | Administrators can issue a single-use reset token. Email delivery and a public recovery-request endpoint are not configured. | Add a rate-limited recovery request and a verified delivery mechanism. Until then, administrators must share the generated token out of band. |
| Product image files | Product image settings accept a validated HTTP(S) URL. | Add authenticated file upload/storage and an image retrieval policy if direct file uploads are required. |
| Audit retention enforcement | The retention setting stores policy, but old audit records are not automatically purged. | Add a scheduled retention job, with audit-safe retention rules and operational visibility. |
| Checkout quote / tax preview | The backend calculates invoice tax and discounts during invoice processing; there is no dedicated quote endpoint. | Add a quote endpoint that uses the same server-side pricing policy as invoice finalization. Until then, the UI labels displayed totals as estimates and the server response remains authoritative. |

## API behaviors the frontend already supports

- Session login/logout, CSRF token handling, account activation, reset-token issuance, active-session listing, and administrator force logout.
- Inventory adjustments, counts, stock movements, and reorder suggestions.
- Invoice holds, recall, finalization, printing, returns, and voids.
- Purchase approval, receiving, and returns.
- Sales/inventory reports, date-range comparison, CSV/PDF exports, and sales analytics.
- Per-user notification preferences, payment modes, store profile, and session policy settings.

See the backend [README.md](../../../backend/README.md) for the full endpoint list, role model, and setup instructions.
