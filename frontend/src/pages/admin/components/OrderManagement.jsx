import { Search } from "lucide-react";
import { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { toast } from "react-toastify";
import LoadingState from "../../../components/LoadingState";
import { cancelOrder, getOrderPage } from "../../../services/orderService";
import { orderStatusLabel } from "../../../utils/orderStatus";

const ORDER_PAGE_SIZE = 10;
const CANCELLABLE_STATUSES = ["PENDING_INVENTORY", "CONFIRMED", "PARTIALLY_CONFIRMED", "REJECTED"];
const ORDER_STATUSES = ["PENDING_INVENTORY", "CONFIRMED", "PARTIALLY_CONFIRMED", "REJECTED", "CANCELLATION_PENDING", "CANCELLED"];

export function OrderManagement() {
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
  const [updatingIds, setUpdatingIds] = useState([]);
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
    getOrderPage(params.toString(), token)
      .then((result) => {
        if (!active) return;
        setOrders(result?.content || []);
        setPages(result?.totalPages || 0);
        setTotal(result?.totalElements || 0);
      })
      .catch((error) => {
        if (active) toast.error(error.message || "Could not load orders.");
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

  const requestCancellation = async (order) => {
    setUpdatingIds((current) => [...current, order.id]);
    try {
      await cancelOrder(order.id, token);
      toast.success(`Cancellation requested for order #${order.id.slice(-8)}.`);
      setRefresh((current) => current + 1);
    } catch (error) {
      toast.error(error.message || "Could not cancel order.");
    } finally {
      setUpdatingIds((current) => current.filter((id) => id !== order.id));
    }
  };

  return (
    <section className="mt-8 border border-[#e4e6de] bg-white p-5 sm:p-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-lg font-medium text-[#354237]">Order management</h2>
          <p className="mt-1 text-xs text-[#858a80]">{total.toLocaleString()} orders · {ORDER_PAGE_SIZE} per page. Admins and managers can cancel pending, confirmed, partially confirmed, or rejected orders here.</p>
        </div>
        <div className="flex w-full flex-wrap gap-2 sm:w-auto">
          <form onSubmit={submitSearch} className="flex gap-2">
            <input type="search" value={searchInput} onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Order ID or email..." aria-label="Search orders"
              className="h-10 min-w-0 flex-1 border border-[#dfe2d9] px-3 text-sm outline-none focus:border-[#71816d] sm:w-60" />
            <button type="submit" aria-label="Search orders" className="grid h-10 w-10 place-items-center bg-[#314638] text-white hover:bg-[#25372b]"><Search size={16} /></button>
          </form>
          <select aria-label="Filter orders by status" value={statusFilter} onChange={(event) => { setPage(0); setStatusFilter(event.target.value); }}
            className="h-10 border border-[#dfe2d9] bg-white px-3 text-sm">
            <option value="">All statuses</option>
            {ORDER_STATUSES.map((status) => <option key={status} value={status}>{status.replaceAll("_", " ")}</option>)}
          </select>
          <label className="flex h-10 items-center gap-2 border border-[#dfe2d9] px-2 text-xs text-[#687064]">
            From
            <input type="date" aria-label="Orders from date" value={fromDate} max={toDate || undefined}
              onChange={(event) => { setPage(0); setFromDate(event.target.value); }}
              className="min-w-0 bg-transparent text-xs" />
          </label>
          <label className="flex h-10 items-center gap-2 border border-[#dfe2d9] px-2 text-xs text-[#687064]">
            To
            <input type="date" aria-label="Orders to date" value={toDate} min={fromDate || undefined}
              onChange={(event) => { setPage(0); setToDate(event.target.value); }}
              className="min-w-0 bg-transparent text-xs" />
          </label>
          <select aria-label="Sort orders by date" value={dateDirection}
            onChange={(event) => { setPage(0); setDateDirection(event.target.value); }}
            className="h-10 border border-[#dfe2d9] bg-white px-3 text-sm">
            <option value="desc">Newest first</option>
            <option value="asc">Oldest first</option>
          </select>
        </div>
      </div>

      {loading ? <LoadingState label="Loading orders…" compact /> : orders.length === 0 ? (
        <p className="mt-4 border border-[#e4e6de] p-5 text-sm text-[#858a80]">No orders match your search.</p>
      ) : (
        <div className="mt-4 overflow-x-auto border border-[#e4e6de]">
          <table className="w-full min-w-[850px] text-left text-sm">
            <thead className="border-b border-[#e4e6de] bg-[#fbfbf8] text-xs text-[#858a80]">
              <tr><th className="px-4 py-3 font-medium">Order</th><th className="px-4 py-3 font-medium">Customer</th><th className="px-4 py-3 font-medium">Date</th><th className="px-4 py-3 font-medium">Total</th><th className="px-4 py-3 font-medium">Status</th><th className="px-4 py-3 font-medium">Actions</th></tr>
            </thead>
            <tbody>{orders.map((order) => {
              const status = order.status || "PENDING_INVENTORY";
              const busy = updatingIds.includes(order.id);
              return (
                <tr key={order.id} className="border-b border-[#eef0ea] last:border-0 align-top">
                  <td className="px-4 py-3">
                    <details>
                      <summary className="cursor-pointer font-medium text-[#354237]">#{order.id.slice(-8)}</summary>
                      <div className="mt-3 w-[min(760px,calc(100vw-3rem))] space-y-4">
                        <div className="overflow-x-auto border border-[#e4e6de]">
                          <table className="w-full min-w-[620px] text-left text-xs">
                            <thead className="bg-[#fbfbf8] text-[#858a80]">
                              <tr>
                                <th className="px-3 py-2 font-medium">Ordered item</th>
                                <th className="px-3 py-2 font-medium">Color / variant</th>
                                <th className="px-3 py-2 text-right font-medium">Qty</th>
                                <th className="px-3 py-2 text-right font-medium">Unit price</th>
                                <th className="px-3 py-2 text-right font-medium">Line total</th>
                                <th className="px-3 py-2 font-medium">Inventory</th>
                              </tr>
                            </thead>
                            <tbody>
                              {(order.items || []).map((item, index) => {
                                const itemResult = order.itemResults?.find((result) => result.itemIndex === index);
                                const unitPrice = Number(item.unitPrice || 0);
                                return (
                                  <tr key={`${item.productId}-${index}`} className="border-t border-[#eef0ea]">
                                    <td className="px-3 py-2.5 font-medium text-[#354237]">{item.productName}</td>
                                    <td className="px-3 py-2.5 text-[#687064]">{item.color || "Standard"}</td>
                                    <td className="px-3 py-2.5 text-right text-[#687064]">{item.quantity}</td>
                                    <td className="px-3 py-2.5 text-right whitespace-nowrap text-[#687064]">${unitPrice.toFixed(2)}</td>
                                    <td className="px-3 py-2.5 text-right whitespace-nowrap font-medium text-[#354237]">${(unitPrice * item.quantity).toFixed(2)}</td>
                                    <td className="px-3 py-2.5">
                                      {itemResult ? (
                                        <span className={itemResult.status === "RESERVED" ? "text-emerald-700" : "text-red-700"}>
                                          {itemResult.status === "RESERVED" ? "Reserved" : "Unavailable"}
                                          {itemResult.message && itemResult.status !== "RESERVED" && <span className="block max-w-48 whitespace-normal">{itemResult.message}</span>}
                                        </span>
                                      ) : <span className="text-[#92978e]">Checking</span>}
                                    </td>
                                  </tr>
                                );
                              })}
                            </tbody>
                            <tfoot className="border-t border-[#e4e6de] bg-[#fbfbf8]">
                              <tr>
                                <td colSpan="4" className="px-3 py-2 text-right font-medium text-[#687064]">Order total</td>
                                <td className="px-3 py-2 text-right font-semibold text-[#354237]">${Number(order.total || 0).toFixed(2)}</td>
                                <td />
                              </tr>
                            </tfoot>
                          </table>
                        </div>
                        {order.shippingAddress && (() => {
                          const address = order.shippingAddress;
                          const addressParts = [address.line1, address.line2, address.city, address.region]
                            .filter(Boolean)
                            .filter((part, index, parts) => parts.findIndex(
                              (candidate) => candidate.trim().toLowerCase() === part.trim().toLowerCase(),
                            ) === index);
                          return (
                            <div className="text-xs leading-5 text-[#687064]">
                              <p className="font-medium text-[#354237]">Delivery address</p>
                              <p>{addressParts.join(", ")} {address.postalCode}</p>
                              <p>{address.country}</p>
                            </div>
                          );
                        })()}
                      </div>
                    </details>
                  </td>
                  <td className="px-4 py-3 text-[#687064]">{order.shippingAddress?.recipientName || order.email || "—"}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-[#687064]">{order.createdAt ? new Date(order.createdAt).toLocaleDateString() : "—"}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-[#687064]">${Number(order.total || 0).toFixed(2)}</td>
                  <td className="px-4 py-3"><span className="inline-block rounded-full bg-[#e9ece5] px-2.5 py-1 text-[11px] font-medium text-[#52644f]">{orderStatusLabel(status)}</span></td>
                  <td className="px-4 py-3">
                    {CANCELLABLE_STATUSES.includes(status) ? (
                      <button type="button" disabled={busy} onClick={() => requestCancellation(order)}
                        className="rounded border border-red-200 px-2.5 py-1 text-xs text-red-700 hover:bg-red-50 disabled:opacity-50">
                        {busy ? "Cancelling…" : "Cancel order"}
                      </button>
                    ) : <span className="text-xs text-[#92978e]">—</span>}
                  </td>
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
