# STOCKLY Billing

STOCKLY is a Spring Boot retail billing/inventory API and a separate Next.js web application.

## Project structure

```text
billing/
├── backend/   Spring Boot, Maven wrapper, resources, and tests
├── frontend/  Next.js, TypeScript, public assets, and frontend docs
├── docs/      Shared project documentation
├── .gitignore
└── README.md
```

`.vscode/` and `.gitattributes` remain at the project root as workspace/repository metadata. Keep one Git repository at this root if you initialize or attach this project to Git; do not create nested repositories.

## Database configuration

The backend uses MySQL by default. Create a database/user as appropriate and set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the environment. The schema uses Hibernate `ddl-auto=update`; review database migration policy before production use.

Copy [backend/.env.example](backend/.env.example) to `backend/.env` for a placeholder reference, then set real values locally. Spring Boot does not load `.env` files automatically: export these variables in the process environment or configure your IDE/run tool to load them. Never commit `.env` or real credentials. The bootstrap admin is created only on an empty user table when all `APP_BOOTSTRAP_ADMIN_*` values are set.

## Run the backend

From `backend/`, with Java 17+ and MySQL available:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

The API listens on port 8080 by default. Backend endpoints and security details are documented in [backend/README.md](backend/README.md).

## Run the frontend

From `frontend/`:

```powershell
npm ci
npm run dev
```

Open `http://localhost:3000`. To change the backend origin, set `BACKEND_ORIGIN` using `frontend/.env.example` as a reference and restart Next.js.

## Frontend-to-backend connection

The frontend sends same-origin `/api/...` requests. The Next.js rewrite in `frontend/next.config.ts` proxies them to `BACKEND_ORIGIN` (default `http://localhost:8080`). The Spring API uses session cookies and CSRF tokens; the frontend client handles both. No browser CORS configuration is needed for this proxy arrangement.

Public registration creates an inactive `BILLING_STAFF` account; an administrator must activate it before login. Login supports email or username. See the [frontend README](frontend/README.md) and [documentation index](docs/README.md).
