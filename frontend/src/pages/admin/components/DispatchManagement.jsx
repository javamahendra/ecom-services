import { Search } from "lucide-react";
import { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { toast } from "react-toastify";
import LoadingState from "../../../components/LoadingState";
import { cancelOrder, getDispatches, syncDispatches, updateDispatchStatus } from "../../../services/orderService";

const ORDER_PAGE_SIZE = 10;
const nextDispatchStatuses = {
  AWAITING_INVENTORY: ["CANCELLED"],
  INVENTORY_REJECTED: ["CANCELLED"],
  READY_TO_DISPATCH: ["PACKED", "CANCELLED"],
  PACKED: ["SHIPPED", "CANCELLED"],
  SHIPPED: ["IN_TRANSIT"],
  IN_TRANSIT: ["DELIVERED"],
};

export function DispatchManagement({ readOnly = false }) {

  const token = useSelector((state) => state.auth.token);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [dateDirection, setDateDirection] = useState("desc");
  const [trackingNumbers, setTrackingNumbers] = useState({});
  const [updatingIds, setUpdatingIds] = useState([]);
  const [syncing, setSyncing] = useState(false);
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    let active = true;
    const params = new URLSearchParams({
      page: String(page),
      size: String(ORDER_PAGE_SIZE),
      sortBy: "createdAt",
      direction: dateDirection,
    });
    if (search) params.set("search", search);
    if (statusFilter) params.set("status", statusFilter);
    if (fromDate) params.set("fromDate", fromDate);
    if (toDate) params.set("toDate", toDate);
    setLoading(true);
    getDispatches(params.toString(), token)
      .then((result) => {
        if (!active) return;
        setOrders(result?.content || []);
        setPages(result?.totalPages || 0);
        setTotal(result?.totalElements || 0);
      })
      .catch((error) => {
        if (active) toast.error(error.message || "Could not load dispatches.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [dateDirection, fromDate, page, refresh, search, statusFilter, toDate, token]);

  const submitSearch = (event) => {
    event.preventDefault();
    setPage(0);
    setSearch(searchInput.trim());
  };

  const changeStatus = async (order, status) => {
    setUpdatingIds((current) => [...current, order.orderId]);
    try {
      if (status === "CANCELLED") {
        await cancelOrder(order.orderId, token);
      } else {
        await updateDispatchStatus(order.orderId, {
          status,
          trackingNumber: trackingNumbers[order.orderId] || undefined,
        }, token);
      }
      toast.success(`Order #${order.orderId.slice(-8)} updated to ${status.replaceAll("_", " ").toLowerCase()}.`);
      setRefresh((current) => current + 1);
    } catch (error) {
      toast.error(error.message || "Could not update order status.");
    } finally {
      setUpdatingIds((current) => current.filter((id) => id !== order.orderId));
    }
  };

  const synchronizeExistingOrders = async () => {
    setSyncing(true);
    try {
      const result = await syncDispatches(token);
      toast.success(`${result?.queued || 0} existing orders queued; refresh shortly if they do not appear.`);
      setRefresh((current) => current + 1);
    } catch (error) {
      toast.error(error.message || "Could not synchronize existing orders.");
    } finally {
      setSyncing(false);
    }
  };

  return (
    <section className="mt-8 border border-[#e4e6de] bg-white p-5 sm:p-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-lg font-medium text-[#354237]">{readOnly ? "Your dispatches" : "Dispatch management"}</h2>
          <p className="mt-1 text-xs text-[#858a80]">{total.toLocaleString()} dispatches · {ORDER_PAGE_SIZE} per page</p>
        </div>
        <div className="flex w-full flex-wrap gap-2 sm:w-auto sm:items-end">
          <form onSubmit={submitSearch} className="flex gap-2">
            <input type="search" value={searchInput} onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Order ID, city, tracking..." aria-label="Search orders"
              className="h-10 min-w-0 flex-1 border border-[#dfe2d9] px-3 text-sm outline-none focus:border-[#71816d] sm:w-60" />
            <button type="submit" aria-label="Search orders" className="grid h-10 w-10 place-items-center bg-[#314638] text-white hover:bg-[#25372b]"><Search size={16} /></button>
          </form>
          <select aria-label="Filter orders by status" value={statusFilter} onChange={(event) => { setPage(0); setStatusFilter(event.target.value); }}
            className="h-10 border border-[#dfe2d9] bg-white px-3 text-sm">
            <option value="">All statuses</option>
            {["AWAITING_INVENTORY", "INVENTORY_REJECTED", "READY_TO_DISPATCH", "PACKED", "SHIPPED", "IN_TRANSIT", "DELIVERED", "CANCELLED"].map((status) =>
              <option key={status} value={status}>{status.replaceAll("_", " ")}</option>)}
          </select>
          <label className="flex h-10 items-center gap-2 border border-[#dfe2d9] px-2 text-xs text-[#687064]">
            From
            <input type="date" aria-label="Dispatches from date" value={fromDate} max={toDate || undefined}
              onChange={(event) => { setPage(0); setFromDate(event.target.value); }}
              className="min-w-0 bg-transparent text-xs" />
          </label>
          <label className="flex h-10 items-center gap-2 border border-[#dfe2d9] px-2 text-xs text-[#687064]">
            To
            <input type="date" aria-label="Dispatches to date" value={toDate} min={fromDate || undefined}
              onChange={(event) => { setPage(0); setToDate(event.target.value); }}
              className="min-w-0 bg-transparent text-xs" />
          </label>
          <select aria-label="Sort dispatches by date" value={dateDirection}
            onChange={(event) => { setPage(0); setDateDirection(event.target.value); }}
            className="h-10 border border-[#dfe2d9] bg-white px-3 text-sm">
            <option value="desc">Newest first</option>
            <option value="asc">Oldest first</option>
          </select>
          {!readOnly && <button type="button" disabled={syncing} onClick={synchronizeExistingOrders}
            className="h-10 whitespace-nowrap bg-[#314638] px-4 text-xs font-medium text-white hover:bg-[#25372b] disabled:cursor-wait disabled:opacity-60">
            {syncing ? "Syncing…" : "Sync existing orders"}
          </button>}
        </div>
      </div>

      {loading ? <LoadingState label="Loading orders…" compact /> : orders.length === 0 ? (
        <p className="mt-4 border border-[#e4e6de] p-5 text-sm text-[#858a80]">No dispatches match your search. Staff can sync existing orders to create missing dispatch records.</p>
      ) : (
        <div className="mt-4 overflow-x-auto border border-[#e4e6de]">
          <table className={`w-full ${readOnly ? "min-w-[700px]" : "min-w-[900px]"} text-left text-sm`}>
            <thead className="border-b border-[#e4e6de] bg-[#fbfbf8] text-xs text-[#858a80]">
              <tr><th className="px-4 py-3 font-medium">Order</th>{!readOnly && <th className="px-4 py-3 font-medium">Customer</th>}<th className="px-4 py-3 font-medium">Date</th><th className="px-4 py-3 font-medium">Total</th><th className="px-4 py-3 font-medium">Status</th>{readOnly ? <th className="px-4 py-3 font-medium">Tracking</th> : <th className="px-4 py-3 font-medium">Actions</th>}</tr>
            </thead>
            <tbody>{orders.map((order) => {
              const status = order.status || "AWAITING_INVENTORY";
              const nextStatuses = nextDispatchStatuses[status] || [];
              const busy = updatingIds.includes(order.orderId);
              return (
                <tr key={order.orderId} className="border-b border-[#eef0ea] last:border-0 align-top">
                  <td className="px-4 py-3">
                    <details>
                      <summary className="cursor-pointer font-medium text-[#354237]">#{order.orderId.slice(-8)}</summary>
                      <div className="mt-3 w-[min(760px,calc(100vw-3rem))] space-y-4">
                        <div className="overflow-x-auto border border-[#e4e6de]">
                          <table className="w-full min-w-[560px] text-left text-xs">
                            <thead className="bg-[#fbfbf8] text-[#858a80]">
                              <tr>
                                <th className="px-3 py-2 font-medium">Item to dispatch</th>
                                <th className="px-3 py-2 font-medium">Color / variant</th>
                                <th className="px-3 py-2 text-right font-medium">Qty</th>
                                <th className="px-3 py-2 text-right font-medium">Unit price</th>
                                <th className="px-3 py-2 text-right font-medium">Line total</th>
                              </tr>
                            </thead>
                            <tbody>
                              {(order.items || []).map((item, index) => {
                                const unitPrice = Number(item.unitPrice || 0);
                                return (
                                  <tr key={`${item.productId}-${index}`} className="border-t border-[#eef0ea]">
                                    <td className="px-3 py-2.5 font-medium text-[#354237]">{item.productName}</td>
                                    <td className="px-3 py-2.5 text-[#687064]">{item.color || "Standard"}</td>
                                    <td className="px-3 py-2.5 text-right text-[#687064]">{item.quantity}</td>
                                    <td className="px-3 py-2.5 text-right whitespace-nowrap text-[#687064]">${unitPrice.toFixed(2)}</td>
                                    <td className="px-3 py-2.5 text-right whitespace-nowrap font-medium text-[#354237]">${(unitPrice * item.quantity).toFixed(2)}</td>
                                  </tr>
                                );
                              })}
                            </tbody>
                          </table>
                        </div>
                        {order.shippingAddress && (
                          <div className="text-xs leading-5 text-[#687064]">
                            <p className="font-medium text-[#354237]">Delivery address</p>
                            <p>{order.shippingAddress.line1}{order.shippingAddress.line2 ? `, ${order.shippingAddress.line2}` : ""}</p>
                            <p>{order.shippingAddress.city}, {order.shippingAddress.region} {order.shippingAddress.postalCode}</p>
                          </div>
                        )}
                      </div>
                    </details>
                  </td>
                  {!readOnly && <td className="px-4 py-3 text-[#687064]">{order.shippingAddress?.recipientName || order.email || "—"}</td>}
                  <td className="px-4 py-3 whitespace-nowrap text-[#687064]">{order.createdAt ? new Date(order.createdAt).toLocaleDateString() : "—"}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-[#687064]">${Number(order.total || 0).toFixed(2)}</td>
                  <td className="px-4 py-3"><span className="inline-block rounded-full bg-[#e9ece5] px-2.5 py-1 text-[11px] font-medium text-[#52644f]">{status.replaceAll("_", " ")}</span></td>
                  {readOnly ? <td className="px-4 py-3 text-[#687064]">{order.trackingNumber || "—"}</td> : <td className="px-4 py-3">
                    <div className="flex min-w-48 flex-col items-start gap-2">
                      {nextStatuses.includes("SHIPPED") && <input value={trackingNumbers[order.orderId] || ""}
                        onChange={(event) => setTrackingNumbers((current) => ({ ...current, [order.orderId]: event.target.value }))}
                        placeholder="Tracking number (optional)" aria-label={`Tracking number for order ${order.orderId}`}
                        className="h-8 w-full border border-[#dfe2d9] px-2 text-xs" />}
                      {nextStatuses.map((nextStatus) => (
                        <button key={nextStatus} type="button" disabled={busy}
                          onClick={() => changeStatus(order, nextStatus)}
                          className={`rounded border px-2.5 py-1 text-xs disabled:opacity-50 ${nextStatus === "CANCELLED" ? "border-red-200 text-red-700 hover:bg-red-50" : "border-[#dfe2d9] text-[#52644f] hover:bg-[#f4f5f0]"}`}>
                          {busy ? "Updating…" : nextStatus === "CANCELLED" ? "Cancel order" : `Mark ${nextStatus.replaceAll("_", " ").toLowerCase()}`}
                        </button>
                      ))}
                      {nextStatuses.length === 0 && <span className="text-xs text-[#92978e]">No further actions</span>}
                    </div>
                  </td>}
                </tr>
              );
            })}</tbody>
          </table>
        </div>
      )}
      {pages > 1 && <div className="mt-4 flex items-center justify-between">
        <button type="button" disabled={page === 0 || loading} onClick={() => setPage((current) => current - 1)}
          className="rounded border border-[#dfe2d9] px-3 py-2 text-xs text-[#52644f] disabled:opacity-40">Previous</button>
        <span className="text-xs text-[#687064]">Page {page + 1} of {pages}</span>
        <button type="button" disabled={page + 1 >= pages || loading} onClick={() => setPage((current) => current + 1)}
          className="rounded border border-[#dfe2d9] px-3 py-2 text-xs text-[#52644f] disabled:opacity-40">Next</button>
      </div>}
    </section>
  );
}
