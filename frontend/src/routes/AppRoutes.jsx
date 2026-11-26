import { lazy } from "react";
import { Route, Routes } from "react-router-dom";

import Cart from "../pages/Cart";
import Home from "../pages/Home";
import ProductDetails from "../pages/ProductDetails";

const LoginPage = lazy(() => import("../pages/login/Login"));
const RegisterPage = lazy(() => import("../pages/register/Register"));
const AccountOverview = lazy(() => import("../pages/account/AccountOverview"));
const AddressesPage = lazy(() => import("../pages/account/Addresses"));
const WishlistPage = lazy(() => import("../pages/account/Wishlist"));
const OrderDetailsPage = lazy(() => import("../pages/account/OrderDetails"));
const DispatchesPage = lazy(() => import("../pages/dispatch/DispatchesPage"));
const AdminDashboard = lazy(() => import("../pages/admin/AdminDashboard"));
const InventoryManagement = lazy(() => import("../pages/admin/InventoryManagement"));
const CheckoutPage = lazy(() => import("../pages/checkout/Checkout"));
const PanelLayout = lazy(() => import("../components/panels/PanelLayout"));
const RequireAuth = lazy(() => import("../components/panels/RequireAuth"));

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/products/:id" element={<ProductDetails />} />
      <Route path="/cart" element={<Cart />} />
      <Route path="/checkout" element={<RequireAuth><CheckoutPage /></RequireAuth>} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/account" element={<RequireAuth deniedRoles={["admin", "manager"]}><PanelLayout panel="user" /></RequireAuth>}>
        <Route index element={<AccountOverview />} />
        <Route path="orders" element={<AccountOverview ordersOnly />} />
        <Route path="orders/:orderId" element={<OrderDetailsPage />} />
        <Route path="dispatches" element={<DispatchesPage />} />
        <Route path="wishlist" element={<WishlistPage />} />
        <Route path="addresses" element={<AddressesPage />} />
      </Route>
      <Route path="/admin" element={<RequireAuth role="admin"><PanelLayout panel="admin" /></RequireAuth>}>
        <Route index element={<AdminDashboard />} />
        <Route path="products" element={<AdminDashboard view="products" />} />
        <Route path="inventory" element={<InventoryManagement />} />
        <Route path="orders" element={<AdminDashboard view="orders" />} />
        <Route path="dispatches" element={<AdminDashboard view="dispatches" />} />
        <Route path="users" element={<AdminDashboard view="users" />} />
        <Route path="banner" element={<AdminDashboard view="banner" />} />
      </Route>
      <Route path="/manager" element={<RequireAuth role="manager"><PanelLayout panel="manager" /></RequireAuth>}>
        <Route index element={<AdminDashboard managerMode />} />
        <Route path="products" element={<AdminDashboard managerMode view="products" />} />
        <Route path="orders" element={<AdminDashboard managerMode view="orders" />} />
        <Route path="dispatches" element={<AdminDashboard managerMode view="dispatches" />} />
      </Route>
      <Route
        path="*"
        element={(
          <main className="mx-auto max-w-3xl px-6 py-32 text-center">
            <h1 className="text-3xl text-[#29382d]">Page not found.</h1>
            <a href="/" className="mt-5 inline-block text-sm text-[#52644f]">Return to the store</a>
          </main>
        )}
      />
    </Routes>
  );
}
