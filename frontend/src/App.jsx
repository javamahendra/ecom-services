import { Route, Routes, useLocation } from "react-router-dom";
import { lazy, Suspense, useEffect, useState } from "react";
import Navbar from "./components/Navbar";
import Footer from "./components/Footer";
import Home from "./pages/Home";
import ProductDetails from "./pages/ProductDetails";
import Cart from "./pages/Cart";
import LoadingState from "./components/LoadingState";

const LoginPage = lazy(() => import("./pages/login/Login"));
const RegisterPage = lazy(() => import("./pages/register/Register"));
const AccountOverview = lazy(() => import("./pages/account/AccountOverview"));
const AddressesPage = lazy(() => import("./pages/account/Addresses"));
const WishlistPage = lazy(() => import("./pages/account/Wishlist"));
const OrderDetailsPage = lazy(() => import("./pages/account/OrderDetails"));
const AdminDashboard = lazy(() => import("./pages/admin/AdminDashboard"));
const CheckoutPage = lazy(() => import("./pages/checkout/Checkout"));
const PanelLayout = lazy(() => import("./components/panels/PanelLayout"));
const RequireAuth = lazy(() => import("./components/panels/RequireAuth"));
const SessionActivityMonitor = lazy(() => import("./components/panels/SessionActivityMonitor"));

function ScrollToTop() {
  const { pathname } = useLocation();
  useEffect(() => { window.scrollTo({ top: 0, behavior: "smooth" }); }, [pathname]);
  return null;
}

function RouteLoadingIndicator() {
  const { pathname } = useLocation();
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    setVisible(true);
    const timer = window.setTimeout(() => setVisible(false), 500);
    return () => window.clearTimeout(timer);
  }, [pathname]);

  if (!visible) return null;
  return (
    <div className="pointer-events-none fixed inset-x-0 top-0 z-[100] flex justify-center px-4 pt-3" role="status" aria-live="polite">
      <div className="flex items-center gap-2 rounded-full border border-[#e4e6de] bg-white/95 px-4 py-2 text-xs text-[#586557] shadow-lg backdrop-blur">
        <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-[#dfe5dc] border-t-[#314638]" />
        Loading page…
      </div>
    </div>
  );
}

export default function App() {
  const { pathname } = useLocation();
  const isPanel = pathname.startsWith("/account") || pathname.startsWith("/admin") || pathname.startsWith("/manager");

  return (
    <>
      <ScrollToTop />
      <RouteLoadingIndicator />
      <Suspense fallback={null}><SessionActivityMonitor /></Suspense>
      {!isPanel && <Navbar />}
      <Suspense fallback={<main className="mx-auto min-h-[50vh] max-w-3xl px-6"><LoadingState label="Loading page…" /></main>}>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/products/:id" element={<ProductDetails />} />
          <Route path="/cart" element={<Cart />} />
          <Route path="/checkout" element={<RequireAuth><CheckoutPage /></RequireAuth>} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/account" element={<RequireAuth><PanelLayout panel="user" /></RequireAuth>}>
            <Route index element={<AccountOverview />} />
            <Route path="orders" element={<AccountOverview ordersOnly />} />
            <Route path="orders/:orderId" element={<OrderDetailsPage />} />
            <Route path="wishlist" element={<WishlistPage />} />
            <Route path="addresses" element={<AddressesPage />} />
          </Route>
          <Route path="/admin" element={<RequireAuth role="admin"><PanelLayout panel="admin" /></RequireAuth>}>
            <Route index element={<AdminDashboard />} />
            <Route path="products" element={<AdminDashboard view="products" />} />
            <Route path="orders" element={<AdminDashboard view="orders" />} />
            <Route path="users" element={<AdminDashboard view="users" />} />
            <Route path="banner" element={<AdminDashboard view="banner" />} />
          </Route>
          <Route path="/manager" element={<RequireAuth role="manager"><PanelLayout panel="manager" /></RequireAuth>}>
            <Route index element={<AdminDashboard managerMode />} />
            <Route path="products" element={<AdminDashboard managerMode view="products" />} />
            <Route path="orders" element={<AdminDashboard managerMode view="orders" />} />
          </Route>
          <Route path="*" element={<main className="mx-auto max-w-3xl px-6 py-32 text-center"><h1 className="text-3xl text-[#29382d]">Page not found.</h1><a href="/" className="mt-5 inline-block text-sm text-[#52644f]">Return to the store</a></main>} />
        </Routes>
      </Suspense>
      {!isPanel && <Footer />}
    </>
  );
}
