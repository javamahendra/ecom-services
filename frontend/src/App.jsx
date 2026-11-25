import { useLocation } from "react-router-dom";
import { lazy, Suspense, useEffect, useState } from "react";
import Navbar from "./components/Navbar";
import Footer from "./components/Footer";
import LoadingState from "./components/LoadingState";
import AppRoutes from "./routes/AppRoutes";

const SessionActivityMonitor = lazy(() => import("./components/panels/SessionActivityMonitor"));

function ScrollToTop() {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, [pathname]);

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
        Loading page...
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
      <Suspense fallback={<main className="mx-auto min-h-[50vh] max-w-3xl px-6"><LoadingState label="Loading page..." /></main>}>
        <AppRoutes />
      </Suspense>
      {!isPanel && <Footer />}
    </>
  );
}
