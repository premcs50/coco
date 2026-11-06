# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.2/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.2/maven-plugin/build-image.html)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.1.2/reference/using/devtools.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.2/reference/web/servlet.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.2/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Validation](https://docs.spring.io/spring-boot/4.1.2/reference/io/validation.html)
* [Spring Security](https://docs.spring.io/spring-boot/4.1.2/reference/web/spring-security.html)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/4.1.2/reference/actuator/index.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Validation](https://spring.io/guides/gs/validating-form-input/)
* [Securing a Web Application](https://spring.io/guides/gs/securing-web/)
* [Spring Boot and OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)
* [Authenticating a User with LDAP](https://spring.io/guides/gs/authenticating-ldap/)
* [Accessing data with MySQL](https://spring.io/guides/gs/accessing-data-mysql/)
* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.

## Retail REST API

The API is rooted at `/api`. All endpoints require an authenticated session unless noted. JSON validation errors use a response with `status`, `error`, `message`, and `timestamp`. Role names are `ADMIN`, `STORE_MANAGER`, `INVENTORY_STAFF`, and `BILLING_STAFF`.

### Configure and run

The application uses MySQL by default. Configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for the database before starting. Set `APP_BOOTSTRAP_ADMIN_USERNAME`, `APP_BOOTSTRAP_ADMIN_EMAIL`, and `APP_BOOTSTRAP_ADMIN_PASSWORD` together on the first run to create the initial administrator. The bootstrap password must be at least 10 characters and contain upper- and lowercase letters, a number, and a symbol. The bootstrap account is created only when the user table is empty.

For example, set those variables in the process environment and start with `.\mvnw.cmd spring-boot:run`. Never use the bootstrap credentials as application defaults. `SESSION_TIMEOUT_MINUTES` defaults to 30 and `SESSION_COOKIE_SECURE` should be `true` behind HTTPS. Schema updates use Hibernate `ddl-auto=update`; use reviewed migrations for production deployments.

First request `GET /api/auth/csrf`, then send the returned CSRF token in the `X-XSRF-TOKEN` header and retain the session cookie for state-changing requests. `POST /api/auth/login` accepts `{"username":"...","password":"..."}` and returns the role and role-appropriate `home` API path. `POST /api/auth/logout` invalidates the session; an open shift must be closed first (or explicitly use `?force=true`). `GET /api/auth/me` and `/api/auth/session` expose the current user, idle time, and shift-close hint.

Password reset tokens are issued by an administrator through `POST /api/admin/users/{id}/password-reset-token` and must be passed to the user through a verified out-of-band channel. The token can be used once at `POST /api/auth/password-reset` and expires after 30 minutes. Email delivery is not configured in this project.

Public sign-up uses `POST /api/users` and always creates an inactive `BILLING_STAFF` account; the requested role is never trusted for public registrations. The API stores first name, last name, mobile number, email, username, and a BCrypt password hash. An administrator must activate public sign-ups before they can log in. Authenticated administrators can create supported staff roles; store managers can create billing staff only. The login endpoint accepts either username or email in its `username` field. Product images are stored as validated HTTP(S) URLs rather than uploaded files. Audit entries are retained in the database; the retention setting records policy but does not automatically delete audit history.

### Endpoint coverage

| Epic | Main endpoints |
|---|---|
| 01–02 Accounts and login | `POST /api/users`, `GET /api/users`, `PUT /api/users/{id}/active`, `POST /api/auth/login`, `POST /api/auth/password-reset`, `GET /api/auth/me`, `GET /api/auth/session`, `GET /api/admin/sessions` |
| 03 Catalog | `POST/GET /api/products`, `GET /api/products/{id}`, `PUT /api/products/{id}`, `PUT /api/products/{id}/active`, `PUT /api/products/{id}/image`, `POST /api/products/import`, `POST /api/products/import/csv`, `POST/GET/PUT /api/categories` |
| 04 Inventory | `GET /api/inventory/stock`, `GET /api/inventory/low-stock`, `POST /api/inventory/adjustments`, `POST /api/inventory/counts`, `GET /api/inventory/movements`, `GET /api/inventory/reorder-suggestions`, `PUT /api/settings/negative-stock` |
| 05 Billing | `POST/GET /api/invoices`, `POST /api/invoices/hold`, `GET /api/invoices/held`, `POST /api/invoices/{id}/recall`, `POST /api/invoices/{id}/finalize`, `GET /api/invoices/{id}`, `GET /api/invoices/{id}/print`, `POST /api/invoices/{id}/returns`, `POST /api/invoices/{id}/void` |
| 06 Purchases | `POST/GET /api/purchases`, `GET /api/purchases/{id}`, `POST /api/purchases/{id}/approve`, `POST /api/purchases/{id}/receive`, `POST /api/purchases/{id}/returns` |
| 07 Customers | `POST/GET /api/customers`, `GET /api/customers/search`, `GET /api/customers/{id}/history`, `PUT /api/customers/{id}`, `PUT /api/customers/{id}/active`, `POST /api/customers/{sourceId}/merge/{targetId}` |
| 08 Reports and search | `GET /api/dashboard`, `GET /api/reports/sales`, `GET /api/reports/inventory`, `GET /api/reports/compare`, `GET /api/search`, `GET /api/reports/{sales,inventory}/export?format=csv|pdf` |
| 09 Suppliers | `POST/GET /api/suppliers`, `PUT /api/suppliers/{id}`, `PUT /api/suppliers/{id}/preferred`, `PUT /api/products/{id}/supplier`, `GET /api/suppliers/{id}/performance` |
| 10 Cash and shifts | `POST/GET /api/shifts`, `GET /api/shifts/current`, `POST /api/shifts/{id}/close`, `POST /api/expenses`, `GET /api/payments/summary`, `POST /api/reconciliation` |
| 11 Sales analysis | `GET /api/analytics/sales-trends`, `/performance`, `/profit-margin`, `/peak-hours`, `/abc`, `/dead-stock`, `/targets` |
| 12 Settings and notifications | `GET /api/notifications`, `PUT /api/notifications/{id}/read`, `PUT /api/notifications/preferences`, `PUT /api/settings/{tax,discount-rules,store-profile,payment-modes,session-policy}` |
| 13 Audit | `GET /api/admin/audit`, `/api/admin/audit/failed-logins`, `/api/admin/audit/export`, `PUT /api/settings/audit-retention` |
| 14 Logout | `POST /api/auth/logout`, `POST /api/admin/users/{id}/force-logout`; idle expiry follows the configured session timeout and session policy |

Use ISO date values (`YYYY-MM-DD`) for report range parameters. Report exports return downloadable CSV or PDF. The application stores store profile, tax, discount, payment-mode, session-policy, and retention values in `store_settings`. Product images are referenced by URL; file hosting is an external concern.
