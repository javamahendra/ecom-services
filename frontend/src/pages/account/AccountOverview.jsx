import { ArrowRight, Package, UserRound } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useSelector } from "react-redux";
import { toast } from "react-toastify";
import { getUserProfile } from "../../services/userService";
import { getOrders } from "../../services/orderService";
import { getTokenClaims } from "../../auth/token";
import LoadingState from "../../components/LoadingState";
import { isOrderProcessing, orderStatusLabel } from "../../utils/orderStatus";

export default function AccountOverview({ ordersOnly = false }) {
  const navigate = useNavigate();
  const user = useSelector((state) => state.auth);
  const [profile, setProfile] = useState(null);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    let loadingTimer;
    const loadingStartedAt = Date.now();
    setLoading(true);
    const claims = getTokenClaims(user.token);
    const tasks = [
      getOrders(0, 20, user.token),
      ...(!ordersOnly && user.id ? [getUserProfile(user.id, user.token)] : []),
    ];
    Promise.all(tasks)
      .then(([orderResult, userProfile]) => {
        if (!active) return;
        setOrders(orderResult?.content || []);
        setProfile(!ordersOnly && userProfile ? userProfile : {
          firstName: claims.given_name || "",
          lastName: claims.family_name || "",
          email: user.email || claims.email || "",
          username: claims.preferred_username || "",
        });
      })
      .catch((error) => {
        if (active) {
          if (!ordersOnly) setProfile({ firstName: claims.given_name || "", lastName: claims.family_name || "", email: claims.email || user.email || "", username: claims.preferred_username || "" });
          toast.error(error.message || "Could not load your account details.");
        }
      })
      .finally(() => {
        const remainingLoadingTime = Math.max(0, 400 - (Date.now() - loadingStartedAt));
        if (active) loadingTimer = window.setTimeout(() => setLoading(false), remainingLoadingTime);
      });
    return () => {
      active = false;
      window.clearTimeout(loadingTimer);
    };
  }, [ordersOnly, user.id, user.token, user.email]);

  useEffect(() => {
    if (!orders.some((order) => isOrderProcessing(order.status))) return undefined;
    let active = true;
    let hasReportedError = false;
    const timer = window.setInterval(() => {
      getOrders(0, 20, user.token)
        .then((result) => {
          if (active) setOrders(result?.content || []);
        })
        .catch((error) => {
          if (active && !hasReportedError) {
            hasReportedError = true;
            toast.error(error.message || "Could not refresh order statuses.");
          }
        });
    }, 5000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [orders, user.token]);

  if (loading) return <LoadingState label={ordersOnly ? "Loading your orders…" : "Loading your account…"} />;

  return (
    <div>
      <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-[#788174]">{ordersOnly ? "Order history" : "Account overview"}</p>
      <h1 className="mt-2 text-3xl font-normal tracking-[-0.04em] text-[#29382d]">{ordersOnly ? "My orders" : `Hello${profile?.firstName ? `, ${profile.firstName}` : ""}.`}</h1>
      {!ordersOnly && (
        <div className="mt-7 grid gap-4 sm:grid-cols-2">
          <div className="border border-[#e4e6de] bg-white p-5">
            <span className="grid h-9 w-9 place-items-center rounded-full bg-[#e9ece5] text-[#52644f]"><UserRound size={17} /></span>
            <h2 className="mt-4 text-sm font-medium text-[#354237]">Your details</h2>
            <p className="mt-2 text-sm text-[#687064]">{[profile?.firstName, profile?.lastName].filter(Boolean).join(" ") || profile?.username || user.name}</p>
            <p className="mt-1 text-xs text-[#858a80]">{profile?.email || user.email}</p>
          </div>
          <Link to="/account/orders" className="border border-[#e4e6de] bg-white p-5 transition hover:border-[#aeb9ac]">
            <span className="grid h-9 w-9 place-items-center rounded-full bg-[#e9ece5] text-[#52644f]"><Package size={17} /></span>
            <h2 className="mt-4 text-sm font-medium text-[#354237]">Your orders</h2>
            <p className="mt-2 text-sm text-[#687064]">{orders.length} recent {orders.length === 1 ? "order" : "orders"}</p>
            <span className="mt-3 inline-flex items-center gap-1 text-xs text-[#52644f]">View order history <ArrowRight size={13} /></span>
          </Link>
        </div>
      )}
      <div className="mt-8">
        <h2 className="text-lg font-medium text-[#354237]">{ordersOnly ? "Recent orders" : "Recent activity"}</h2>
        {orders.length === 0 ? (
          <div className="mt-4 border border-[#e4e6de] bg-white px-5 py-10 text-center">
            <p className="text-sm text-[#687064]">No orders yet.</p>
            <Link to="/" className="mt-3 inline-flex items-center gap-1 text-xs text-[#52644f]">Browse the collection <ArrowRight size={13} /></Link>
          </div>
        ) : (
          <div className="mt-4 overflow-x-auto border border-[#e4e6de] bg-white">
            <table className="w-full min-w-[560px] text-left text-sm">
              <thead className="border-b border-[#e4e6de] bg-[#fbfbf8] text-xs text-[#858a80]"><tr><th className="px-4 py-3 font-medium">Order</th><th className="px-4 py-3 font-medium">Date</th><th className="px-4 py-3 font-medium">Status</th><th className="px-4 py-3 text-right font-medium">Total</th></tr></thead>
              <tbody>{orders.map((order) => {
                const detailsPath = `/account/orders/${encodeURIComponent(order.id)}`;
                return (
                  <tr
                    key={order.id}
                    role="link"
                    tabIndex={0}
                    aria-label={`View details for order ${order.id}`}
                    onClick={() => navigate(detailsPath)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        navigate(detailsPath);
                      }
                    }}
                    className="cursor-pointer border-b border-[#eef0ea] last:border-0 hover:bg-[#fbfbf8] focus-visible:outline focus-visible:outline-2 focus-visible:outline-inset focus-visible:outline-[#52644f]"
                  >
                    <td className="px-4 py-3 font-medium text-[#354237]">#{order.id?.slice(-8)}</td>
                    <td className="px-4 py-3 text-[#687064]">{order.createdAt ? new Date(order.createdAt).toLocaleDateString() : "—"}</td>
                    <td className="px-4 py-3 text-[#687064]">{orderStatusLabel(order.status)}</td>
                    <td className="px-4 py-3 text-right text-[#354237]">${Number(order.total || 0).toFixed(2)}</td>
                  </tr>
                );
              })}</tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
