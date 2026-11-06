# STOCKLY frontend

## Run locally

1. Start the Spring Boot API on `http://localhost:8080` (see [backend/README.md](../backend/README.md)).
2. From this directory, install dependencies and start Next.js:

   ```powershell
   npm install
   npm run dev
   ```

3. Open `http://localhost:3000`. To use another backend origin, copy `.env.example` to `.env.local`, set `BACKEND_ORIGIN`, and restart the frontend.

The browser sends same-origin `/api` requests; the Next.js server proxies them to the backend. The API uses session cookies and CSRF protection.

Public registration creates an inactive `BILLING_STAFF` account through the existing API. A store administrator must activate it before the new user can sign in. Email and username are both accepted at login; sessions follow the backend session policy and do not have a separate remember-me option.

## Checks

```powershell
npm run typecheck
npm run lint
npm run build
npm audit
```

Epic-level coverage is in [USER_STORY_MAPPING.md](public/docs/USER_STORY_MAPPING.md). Backend capabilities needed for the remaining Owner/account and service gaps are in [API_INTEGRATION_GAPS.md](public/docs/API_INTEGRATION_GAPS.md).
