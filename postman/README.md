# BILLING API — offline Postman deliverable

## Files
- `BILLING-API.postman_collection.json` — Postman Collection v2.1 with exactly 14 epic folders and 86 story folders; requests and notes are nested under source story IDs.
- `BILLING-API.environment.json` — importable environment template with empty credential/record-ID slots and safe local defaults.

## Import and run
1. In Postman, choose **Import** and import both JSON files.
2. Select **BILLING API - Offline Template** and set `baseUrl` (default `http://localhost:8080`).
3. Configure only test credentials locally. Backend roles are `ADMIN`, `STORE_MANAGER`, `INVENTORY_STAFF`, and `BILLING_STAFF`; there is no OWNER role.
4. Start with `EP02 USER LOGIN → EP02-US01 User Login → GET /api/auth/csrf`. On an isolated test system, explicitly set `allowWrites=true`, set test credentials, and run login. Postman manages the session cookie; the CSRF token is saved from the response.
5. Use IDs only from an isolated test system. Date values are editable ISO `YYYY-MM-DD`.
6. **Writes are disabled by default.** The collection pre-request script skips every non-GET/HEAD/OPTIONS request while `allowWrites` is not exactly `true`. Login/logout, imports, settings, and business mutations are guarded. Enable writes only after confirming the URL points to a disposable isolated test system; set it back to `false` afterward. Failed-auth probes are guarded because failures may create audit events.
7. Story descriptions identify API gaps/partial support. No unsupported endpoint is invented. No DELETE request is present.

## Coverage and validation
- Paths/methods and payloads were checked against Spring controllers and `service/ApiDtos.java`. Response assertions cover statuses, export/PDF content types, DTO-validation probes, and a fresh-session ADMIN authorization denial. These are source-based assertions, not live verification.
- Every included request has a request-level test script, but most assert status codes only. The collection does not constitute a complete response-schema suite, exhaustive authorization matrix for every role and endpoint, or full CRUD/transaction/rollback test suite. Those require a configured isolated test backend and further test coverage.
- Shared write workflows (invoice/payment, returns/refunds) are referenced rather than duplicated where applicable.
- Known gaps/partial support include registration email verification; public password-reset request/delivery; product image file upload (URL setter only); file-based stock count; automated low-stock delivery; quote endpoint; multi-till aggregate reconciliation; automated audit retention; discount-approval audit; and frontend-only logout cache/redirect/confirmation and logout-to-shift-report link.
- No DELETE mapping was found in the inspected controllers, so none is included.
- Collection/environment JSON syntax and structural counts (14 epics, 86 stories) are locally validated.
- **Postman import/run: NOT TESTED / BLOCKED.** Postman was not invoked here.
- **Actual MySQL/phpMyAdmin connection and live database checks: NOT TESTED / BLOCKED.** No database was accessed. No live database result is claimed.
