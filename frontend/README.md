# Form & Field storefront

Responsive React + Vite storefront using Tailwind CSS, React Router, Redux Toolkit, React Hook Form, Zod, Axios, and React Toastify.

## Run locally

```sh
npm install
npm run dev
```

## Frontend structure and conventions

Keep application code under `src/` and organize it by responsibility:

```text
src/
  auth/                 # Token and session helpers
  components/           # Shared UI, grouped by component purpose
  data/                 # Static or fallback data
  pages/                # Route-level screens, grouped by feature
    admin/
      components/       # Admin stock import, order, and user panels
  routes/               # Route definitions and access rules
  services/             # Feature API calls
  store/                # Redux store and feature slices
  App.jsx               # Shared application shell
  api.js                # Shared HTTP client and response helpers
  index.css             # Global styles and Tailwind entry point
  main.jsx              # React, router, and Redux providers
```

Follow these conventions when adding or changing frontend code:

- Use PascalCase for React component files and exports; use camelCase for helpers, hooks, and service functions.
- Keep route-level composition in `routes/`, screen-specific UI in `pages/`, and reusable UI in `components/`.
- Keep endpoint-specific requests in `services/` and use the shared client in `api.js`; do not call `fetch` or Axios directly from components.
- Keep shared or cross-screen state in Redux slices. Use component state for state that belongs to a single screen or component.
- Keep components focused; extract repeated UI and non-trivial behavior into focused components or helpers.
- Validate submitted form data with the existing React Hook Form and Zod approach, and show request failures through the existing user-facing error/toast patterns.
- Use semantic HTML, accessible labels, and clear loading, empty, and error states for user-facing flows.
- Keep imports explicit, remove unused code, and avoid committing generated output, dependencies, or local environment files.

In local development, Vite proxies `/api` requests to the gateway at `http://localhost:8090`, avoiding browser CORS issues even when Vite selects a different port. Set `VITE_API_URL` in `.env` when the frontend is hosted separately. Product browsing falls back to the included curated collection if the product API is unavailable. Registration and sign-in use `/api/users` and `/api/auth/login`.

Login and registration use React Hook Form with Zod validation. The authenticated user account at `/account` shows profile details and order history. Keycloak realm roles `admin`, `manager`, and `user` control staff access: admins can manage users, banners, orders, and create, search, edit, or delete catalog products at `/admin`; the catalog is server-paginated and loads 20 products at a time. Managers can manage order dispatch statuses and create products at `/manager`; regular users can access their account. API authorization remains enforced by the backend.

API requests use the shared Axios client in `src/api.js`. The configured Redux store is in `src/store/store.js`, with Redux slices in `src/store/slices/`. The shopping bag and session profile are persisted in local storage.

Feature API calls are grouped in `src/services/` (`userService.js`, `productService.js`, `orderService.js`, and `bannerService.js`); these services use the shared request client in `src/api.js`. Keep components focused on UI and feature state, and put endpoint-specific request logic in the matching service.

Admins and managers can bulk update variant stock from the product management panel by dropping or browsing for a CSV/XLSX/XLS file. Use the `productCode,color,stockCount` columns; product codes are 10-character human-readable identifiers shown in the catalog. Imports are limited to 10 MB and 5,000 rows. The backend validates every row before applying any updates.

The admin Product Catalog supports an optional exact **Color count** filter. Enter a non-negative whole number and apply filters to find products with exactly that many color variants; this is the number of variant entries returned by `GET /api/products?colorCount=<count>`. The catalog's Colors column shows a dropdown button with the variant count; clicking it expands the product row to show each color, variant ID, stock quantity, and availability. Product total stock remains the sum of its variant stock quantities.

Each stock import is recorded in MongoDB with the file name, size, SHA-256 checksum, upload time, authenticated uploader, status, and per-variant old/new stock plus product category/type. Admins and managers can review this audit history in the same panel.

The uploader sees file-transfer percentage and receives start/completion toasts with successful and failed row counts. Other admins and managers see active imports, uploader identity, row-processing percentage, and completion notifications through a two-second status refresh. Valid rows are applied even when other rows fail; each failed row and reason is retained in the audit history.

Active sessions refresh access tokens as needed. After 30 minutes without user activity, the storefront signs out, revokes the refresh token, and redirects to sign-in.

Checkout requires a signed-in user. Users can save multiple delivery addresses from checkout or `/account/addresses`, choose a delivery address per order, and mark one saved address as their main address. Checkout submits orders to `/api/orders` and clears the local bag after a successful response.

Admins can edit or hide the storefront home banner from the admin panel. Banner settings are persisted by the product service and served to shoppers through `GET /api/banner`.

Before enabling role management, create the `admin`, `manager`, and `user` realm roles in the `ecomservice` Keycloak realm. Grant the auth-service client service account permissions to manage users and their realm-role mappings. Assign `admin` to the initial administrator from the Keycloak console; admins can then create accounts with another role, change assigned roles, and enable or disable accounts in `/admin`. New public registrations are always assigned `user`; accounts without a recognized role are treated as users by the frontend. Restart the auth-service and product-service after applying backend changes.
