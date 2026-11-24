# Form & Field storefront

Responsive React + Vite storefront using Tailwind CSS, React Router, Redux Toolkit, React Hook Form, Zod, Axios, and React Toastify.

## Run locally

```sh
npm install
npm run dev
```

In local development, Vite proxies `/api` requests to the gateway at `http://localhost:8090`, avoiding browser CORS issues even when Vite selects a different port. Set `VITE_API_URL` in `.env` when the frontend is hosted separately. Product browsing falls back to the included curated collection if the product API is unavailable. Registration and sign-in use `/api/users` and `/api/auth/login`.

Login and registration use React Hook Form with Zod validation. The authenticated user account at `/account` shows profile details and order history. Keycloak realm roles `admin`, `manager`, and `user` control staff access: admins can manage users, banners, orders, and create, search, edit, or delete catalog products at `/admin`; the catalog is server-paginated and loads 20 products at a time. Managers can manage order dispatch statuses and create products at `/manager`; regular users can access their account. API authorization remains enforced by the backend.

API requests use the shared Axios client in `src/api.js`. The configured Redux store is in `src/store/store.js`, with Redux slices in `src/store/slices/`. The shopping bag and session profile are persisted in local storage.

Feature API calls are grouped in `src/services/` (`userService.js`, `productService.js`, `orderService.js`, and `bannerService.js`); these services use the shared request client in `src/api.js`. Keep components focused on UI and feature state, and put endpoint-specific request logic in the matching service.

Admins and managers can bulk update variant stock from the product management panel by dropping or browsing for a CSV/XLSX/XLS file. Use the `productCode,color,stockCount` columns; product codes are 10-character human-readable identifiers shown in the catalog. Imports are limited to 10 MB and 5,000 rows. The backend validates every row before applying any updates.

Each stock import is recorded in MongoDB with the file name, size, SHA-256 checksum, upload time, authenticated uploader, status, and per-variant old/new stock plus product category/type. Admins and managers can review this audit history in the same panel.

The uploader sees file-transfer percentage and receives start/completion toasts with successful and failed row counts. Other admins and managers see active imports, uploader identity, row-processing percentage, and completion notifications through a two-second status refresh. Valid rows are applied even when other rows fail; each failed row and reason is retained in the audit history.

Active sessions refresh access tokens as needed. After 30 minutes without user activity, the storefront signs out, revokes the refresh token, and redirects to sign-in.

Checkout requires a signed-in user. Users can save multiple delivery addresses from checkout or `/account/addresses`, choose a delivery address per order, and mark one saved address as their main address. Checkout submits orders to `/api/orders` and clears the local bag after a successful response.

Admins can edit or hide the storefront home banner from the admin panel. Banner settings are persisted by the product service and served to shoppers through `GET /api/banner`.

Before enabling role management, create the `admin`, `manager`, and `user` realm roles in the `ecomservice` Keycloak realm. Grant the auth-service client service account permissions to manage users and their realm-role mappings. Assign `admin` to the initial administrator from the Keycloak console; admins can then create accounts with another role, change assigned roles, and enable or disable accounts in `/admin`. New public registrations are always assigned `user`; accounts without a recognized role are treated as users by the frontend. Restart the auth-service and product-service after applying backend changes.
