import { FileSpreadsheet, Image, Package, Pencil, Plus, Search, Trash2, Upload, Users, X } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useSelector } from "react-redux";
import { toast } from "react-toastify";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { normalizeProduct } from "../../api";
import LoadingState from "../../components/LoadingState";
import { getBanner, updateBanner } from "../../services/bannerService";
import { createProduct as createProductRequest, deleteProduct as deleteProductRequest, getProducts, getStockImportHistory, importProductStock, updateProduct as updateProductRequest } from "../../services/productService";
import { createUser as createUserRequest, getUsers, updateUserRole, updateUserStatus } from "../../services/userService";
import { cancelOrder, getDispatches, getOrders, updateDispatchStatus } from "../../services/orderService";

const productSchema = z.object({
  name: z.string().trim().min(1, "Enter a product name.").max(200),
  category: z.string().trim().min(1, "Enter a category.").max(100),
  productType: z.string().trim().min(1, "Enter a product type.").max(100),
  baseColor: z.string().trim().min(1, "Enter a color.").max(100),
  description: z.string().max(4000),
  price: z.number({ error: "Enter a valid price." }).min(0.01, "Price must be greater than zero."),
  discount: z.number({ error: "Enter a valid discount." }).min(0).max(100),
  imageUrl: z.string().trim().url("Enter a valid image URL."),
  stockCount: z.number({ error: "Enter a valid stock quantity." }).int().min(0),
});

const inputClass = "mt-1.5 h-10 w-full border border-[#dfe2d9] bg-white px-3 text-sm outline-none focus:border-[#71816d]";
const fieldClass = "block text-xs text-[#5d655a]";

const managedRoles = ["admin", "manager", "user"];
const CATALOG_PAGE_SIZE = 20;
const ORDER_PAGE_SIZE = 10;

function StockImportPanel({ token, onImported }) {
  const currentUser = useSelector((state) => state.auth);
  const userName = currentUser?.username || currentUser?.email || "you";
  const previousImportStatuses = useRef(new Map());
  const initialHistoryLoaded = useRef(false);
  const [file, setFile] = useState(null);
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadPercent, setUploadPercent] = useState(0);
  const [result, setResult] = useState(null);
  const [history, setHistory] = useState([]);
  const [historyPage, setHistoryPage] = useState(0);
  const [historyPages, setHistoryPages] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyRefresh, setHistoryRefresh] = useState(0);

  useEffect(() => {
    let active = true;
    let polling = false;
    const loadHistory = async (showLoading = false) => {
      if (polling) return;
      polling = true;
      if (showLoading) setHistoryLoading(true);
      try {
        const response = await getStockImportHistory(historyPage, token);
        if (active) {
          const entries = response?.content || [];
          const isCurrentUser = (entry) => [currentUser?.username, currentUser?.email]
            .filter(Boolean)
            .some((value) => value.toLowerCase() === (entry.uploadedBy || "").toLowerCase());
          if (initialHistoryLoaded.current && historyPage === 0) {
            entries.forEach((entry) => {
              const previousStatus = previousImportStatuses.current.get(entry.id);
              const isActive = ["PROCESSING", "APPLYING"].includes(entry.status);
              if (!previousStatus && !isCurrentUser(entry)) {
                if (isActive) {
                  toast.info(`${entry.uploadedBy} started stock upload: ${entry.fileName}`);
                } else {
                  const message = `${entry.uploadedBy} finished ${entry.fileName}: ${entry.successRows} succeeded, ${entry.failureRows} failed.`;
                  if (entry.failureRows > 0) toast.warn(message);
                  else toast.success(message);
                }
              } else if (previousStatus && ["PROCESSING", "APPLYING"].includes(previousStatus) && !isActive
                && !isCurrentUser(entry)) {
                const message = `${entry.uploadedBy} finished ${entry.fileName}: ${entry.successRows} succeeded, ${entry.failureRows} failed.`;
                if (entry.failureRows > 0) toast.warn(message);
                else toast.success(message);
              }
              previousImportStatuses.current.set(entry.id, entry.status);
            });
          } else if (!initialHistoryLoaded.current) {
            entries.forEach((entry) => previousImportStatuses.current.set(entry.id, entry.status));
            if (historyPage === 0) initialHistoryLoaded.current = true;
          } else {
            entries.forEach((entry) => previousImportStatuses.current.set(entry.id, entry.status));
          }
          setHistory(entries);
          setHistoryPages(response?.totalPages || 0);
        }
      } catch (error) {
        if (active && showLoading) toast.error(error.message || "Could not load stock import history.");
      } finally {
        polling = false;
        if (active && showLoading) setHistoryLoading(false);
      }
    };
    loadHistory(true);
    const interval = window.setInterval(() => loadHistory(), 2000);
    return () => {
      active = false;
      window.clearInterval(interval);
    };
  }, [currentUser?.email, currentUser?.username, historyPage, historyRefresh, token]);

  const chooseFile = (nextFile) => {
    if (!nextFile) return;
    if (!/\.(csv|xlsx|xls)$/i.test(nextFile.name)) {
      setFile(null);
      setResult(null);
      toast.error("Choose a CSV or Excel file (.csv, .xlsx, .xls).");
      return;
    }
    if (nextFile.size > 10 * 1024 * 1024) {
      setFile(null);
      setResult(null);
      toast.error("Stock import files must be 10 MB or smaller.");
      return;
    }
    setFile(nextFile);
    setResult(null);
  };

  const upload = async () => {
    if (!file) {
      toast.error("Choose a stock file to upload.");
      return;
    }
    setUploading(true);
    setUploadPercent(0);
    toast.info(`Stock upload started by ${userName || "you"}: ${file.name}`);
    try {
      const imported = await importProductStock(file, token, (event) => {
        if (event.total) setUploadPercent(Math.min(100, Math.round((event.loaded * 100) / event.total)));
      });
      setResult(imported);
      setFile(null);
      onImported();
      setHistoryPage(0);
      setHistoryRefresh((current) => current + 1);
      const message = `Stock upload finished: ${imported.successRows} succeeded, ${imported.failureRows} failed.`;
      if (imported.failureRows > 0) toast.warn(message);
      else toast.success(message);
    } catch (error) {
      setHistoryPage(0);
      setHistoryRefresh((current) => current + 1);
      toast.error(`Stock upload failed: ${error.message || "Could not import stock."}`);
    } finally {
      setUploading(false);
    }
  };

  const activeImports = history.filter((item) => ["PROCESSING", "APPLYING"].includes(item.status));
  return (
    <section className="mt-8 border border-[#e4e6de] bg-white p-5 sm:p-6">
      <div className="flex items-center gap-2"><FileSpreadsheet size={17} className="text-[#52644f]" /><h2 className="text-lg font-medium text-[#354237]">Bulk stock update</h2></div>
      <p className="mt-1 text-xs text-[#858a80]">Upload CSV or Excel rows using each product code and color variant. Invalid files are rejected without applying changes.</p>
      <div
        onDragOver={(event) => { event.preventDefault(); setDragging(true); }}
        onDragLeave={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setDragging(false); }}
        onDrop={(event) => { event.preventDefault(); setDragging(false); chooseFile(event.dataTransfer.files?.[0]); }}
        className={`mt-4 flex flex-col items-center border border-dashed p-6 text-center transition-colors ${dragging ? "border-[#52644f] bg-[#f4f5f0]" : "border-[#cfd4c9] bg-[#fbfbf8]"}`}
      >
        <Upload size={20} className="text-[#71816d]" />
        <p className="mt-2 text-sm text-[#52644f]">Drop your stock sheet here</p>
        <p className="mt-1 text-xs text-[#858a80]">CSV, XLSX, or XLS · up to 10 MB · maximum 5,000 rows</p>
        <label className="mt-3 cursor-pointer border border-[#dfe2d9] bg-white px-3 py-2 text-xs text-[#52644f] hover:bg-[#f4f5f0]">
          Browse files
          <input type="file" accept=".csv,.xlsx,.xls" className="sr-only" onChange={(event) => { chooseFile(event.target.files?.[0]); event.target.value = ""; }} />
        </label>
      </div>
      <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0 text-xs text-[#687064]">
          {file ? <span className="truncate">{file.name} · {(file.size / 1024).toFixed(1)} KB</span> : <span>Columns: <code>productCode,color,stockCount</code></span>}
        </div>
        <div className="flex items-center gap-3">
          <a href="data:text/csv;charset=utf-8,productCode%2Ccolor%2CstockCount%0A" download="stock-import-template.csv"
            className="text-xs text-[#52644f] underline underline-offset-2">Download CSV template</a>
          <button type="button" disabled={!file || uploading} onClick={upload}
            className="h-9 bg-[#314638] px-4 text-xs font-medium text-white hover:bg-[#25372b] disabled:cursor-not-allowed disabled:opacity-50">
            {uploading ? "Uploading…" : "Update stock"}
          </button>
        </div>
      </div>
      {uploading && <div role="status" aria-live="polite" className="mt-4 border border-[#dfe2d9] bg-[#fbfbf8] p-3">
        <div className="flex items-center justify-between text-xs text-[#52644f]">
          <span>{uploadPercent < 100 ? "Uploading file…" : "File received; applying stock updates…"}</span>
          <span>{uploadPercent}%</span>
        </div>
        <div className="mt-2 h-2 overflow-hidden bg-[#e4e6de]">
          <div className="h-full bg-[#52644f] transition-[width]" style={{ width: `${uploadPercent}%` }} />
        </div>
      </div>}
      {activeImports.length > 0 && <div className="mt-4 space-y-2" aria-live="polite">
        {activeImports.map((item) => (
          <div key={item.id} className="border border-blue-200 bg-blue-50 p-3 text-xs text-blue-900">
            <div className="flex flex-wrap justify-between gap-2">
              <span><strong>{item.uploadedBy}</strong> is uploading/processing <strong>{item.fileName}</strong></span>
              <span>{item.processedRows} / {item.totalRows} rows · {item.progressPercent}%</span>
            </div>
            <div className="mt-2 h-2 overflow-hidden bg-blue-100">
              <div className="h-full bg-blue-700 transition-[width]" style={{ width: `${item.progressPercent}%` }} />
            </div>
            <p className="mt-1">{item.successRows} succeeded · {item.failureRows} failed so far</p>
          </div>
        ))}
      </div>}
      {result && <div role="status" aria-live="polite" className={`mt-3 border p-3 text-xs ${result.failureRows > 0 ? "border-amber-200 bg-amber-50 text-amber-900" : "border-emerald-200 bg-emerald-50 text-emerald-800"}`}>
        Import {result.importId}: {result.totalRows} rows processed, {result.successRows} succeeded, {result.failureRows} failed.
        <ul className="mt-2 max-h-40 list-inside list-disc space-y-1 overflow-y-auto">
          {(result.updates || []).slice(0, 50).map((update, index) => (
            <li key={`${update.productCode}-${update.color}-${index}`}>
              Row {update.rowNumber}: {update.productCode} · {update.color}: {update.previousStockCount} → {update.stockCount}
            </li>
          ))}
        </ul>
        {result.updates?.length > 50 && <span className="mt-1 block">Showing the first 50 successful rows.</span>}
        {result.failures?.length > 0 && <div className="mt-3 border-t border-amber-200 pt-2">
          <p className="font-medium">Failed rows</p>
          <ul className="mt-1 max-h-32 list-inside list-disc space-y-1 overflow-y-auto">
            {result.failures.slice(0, 50).map((failure, index) => (
              <li key={`${failure.rowNumber}-${index}`}>Row {failure.rowNumber}: {failure.productCode} · {failure.color} — {failure.message}</li>
            ))}
          </ul>
          {result.failures.length > 50 && <span className="mt-1 block">Showing the first 50 failed rows.</span>}
        </div>}
      </div>}
      <div className="mt-6 border-t border-[#e4e6de] pt-5">
        <div className="flex flex-wrap items-end justify-between gap-2">
          <div>
            <h3 className="text-sm font-medium text-[#354237]">Stock import history</h3>
            <p className="mt-1 text-xs text-[#858a80]">Uploader, time, source file, and product/variant stock changes.</p>
          </div>
          <button type="button" onClick={() => setHistoryRefresh((current) => current + 1)}
            className="text-xs text-[#52644f] underline underline-offset-2">Refresh history</button>
        </div>
        {historyLoading ? <LoadingState label="Loading import history…" compact /> : history.length === 0 ? (
          <p className="mt-3 text-xs text-[#858a80]">No stock uploads recorded yet.</p>
        ) : (
          <div className="mt-3 space-y-2">
            {history.map((item) => (
              <details key={item.id} className="border border-[#e4e6de] bg-[#fbfbf8] px-3 py-2">
                <summary className="flex cursor-pointer flex-wrap items-center justify-between gap-x-4 gap-y-1 text-xs text-[#52644f]">
                  <span className="min-w-0 truncate font-medium">{item.fileName}</span>
                  <span>{item.status === "COMPLETED" || item.status === "COMPLETED_WITH_ERRORS" || item.status === "FAILED"
                    ? `${item.successRows} succeeded · ${item.failureRows} failed`
                    : `${item.status} · ${item.progressPercent}%`}</span>
                  <span>{item.uploadedAt ? new Date(item.uploadedAt).toLocaleString() : "Time unavailable"}</span>
                </summary>
                <div className="mt-2 space-y-1 border-t border-[#e4e6de] pt-2 text-xs text-[#687064]">
                  <p>Updated by {item.uploadedBy || item.uploadedById} · {item.fileSize?.toLocaleString()} bytes</p>
                  <p>{item.processedRows} / {item.totalRows} rows processed · {item.successRows} succeeded · {item.failureRows} failed</p>
                  {item.sha256 && <p className="break-all font-mono text-[10px]">SHA-256: {item.sha256}</p>}
                  {item.failureReason && <p className="text-red-700">Error: {item.failureReason}</p>}
                  {(item.changes || []).map((change, index) => (
                    <p key={`${change.productCode}-${change.color}-${index}`}>
                      {change.productCode} · {change.productName} · {change.category} / {change.productType} · {change.color}:
                      {" "}{change.previousStockCount} → {change.stockCount}
                    </p>
                  ))}
                  {(item.failures || []).map((failure, index) => (
                    <p key={`failure-${failure.rowNumber}-${index}`} className="text-red-700">
                      Row {failure.rowNumber}: {failure.productCode} · {failure.color} — {failure.message}
                    </p>
                  ))}
                </div>
              </details>
            ))}
            {historyPages > 1 && <div className="flex items-center justify-between pt-2">
              <button type="button" disabled={historyPage === 0} onClick={() => setHistoryPage((current) => current - 1)}
                className="border border-[#dfe2d9] px-3 py-1.5 text-xs text-[#52644f] disabled:opacity-40">Previous</button>
              <span className="text-xs text-[#687064]">Page {historyPage + 1} of {historyPages}</span>
              <button type="button" disabled={historyPage + 1 >= historyPages} onClick={() => setHistoryPage((current) => current + 1)}
                className="border border-[#dfe2d9] px-3 py-1.5 text-xs text-[#52644f] disabled:opacity-40">Next</button>
            </div>}
          </div>
        )}
      </div>
    </section>
  );
}

const nextDispatchStatuses = {
  AWAITING_INVENTORY: ["CANCELLED"],
  INVENTORY_REJECTED: ["CANCELLED"],
  READY_TO_DISPATCH: ["PACKED", "CANCELLED"],
  PACKED: ["SHIPPED", "CANCELLED"],
  SHIPPED: ["IN_TRANSIT"],
  IN_TRANSIT: ["DELIVERED"],
};

function OrderManagement() {
  const token = useSelector((state) => state.auth.token);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [trackingNumbers, setTrackingNumbers] = useState({});
  const [updatingIds, setUpdatingIds] = useState([]);
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    let active = true;
    const params = new URLSearchParams({
      page: String(page),
      size: String(ORDER_PAGE_SIZE),
      sortBy: "createdAt",
      direction: "desc",
    });
    if (search) params.set("search", search);
    if (statusFilter) params.set("status", statusFilter);
    setLoading(true);
    getDispatches(params.toString(), token)
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
  }, [page, refresh, search, statusFilter, token]);

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

  return (
    <section className="mt-8 border border-[#e4e6de] bg-white p-5 sm:p-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-lg font-medium text-[#354237]">Order management</h2>
          <p className="mt-1 text-xs text-[#858a80]">{total.toLocaleString()} orders · {ORDER_PAGE_SIZE} per page</p>
        </div>
        <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row">
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
        </div>
      </div>

      {loading ? <LoadingState label="Loading orders…" compact /> : orders.length === 0 ? (
        <p className="mt-4 border border-[#e4e6de] p-5 text-sm text-[#858a80]">No orders match your search.</p>
      ) : (
        <div className="mt-4 overflow-x-auto border border-[#e4e6de]">
          <table className="w-full min-w-[900px] text-left text-sm">
            <thead className="border-b border-[#e4e6de] bg-[#fbfbf8] text-xs text-[#858a80]">
              <tr><th className="px-4 py-3 font-medium">Order</th><th className="px-4 py-3 font-medium">Customer</th><th className="px-4 py-3 font-medium">Date</th><th className="px-4 py-3 font-medium">Total</th><th className="px-4 py-3 font-medium">Status</th><th className="px-4 py-3 font-medium">Actions</th></tr>
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
                      <div className="mt-2 max-w-xs space-y-1 text-xs text-[#687064]">
                        {(order.items || []).map((item, index) => <p key={`${item.productId}-${index}`}>{item.productName} × {item.quantity}</p>)}
                        {order.shippingAddress && <p className="pt-1">{order.shippingAddress.line1}, {order.shippingAddress.city}, {order.shippingAddress.region} {order.shippingAddress.postalCode}</p>}
                      </div>
                    </details>
                  </td>
                  <td className="px-4 py-3 text-[#687064]">{order.shippingAddress?.recipientName || order.email || "—"}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-[#687064]">{order.createdAt ? new Date(order.createdAt).toLocaleDateString() : "—"}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-[#687064]">${Number(order.total || 0).toFixed(2)}</td>
                  <td className="px-4 py-3"><span className="inline-block rounded-full bg-[#e9ece5] px-2.5 py-1 text-[11px] font-medium text-[#52644f]">{status.replaceAll("_", " ")}</span></td>
                  <td className="px-4 py-3">
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

function UserManagement() {
  const { token, id: currentUserId } = useSelector((state) => state.auth);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [updatingIds, setUpdatingIds] = useState([]);
  const [form, setForm] = useState({
    username: "", email: "", firstName: "", lastName: "", phone: "", password: "", role: "user",
  });

  const loadUsers = useCallback(async () => {
    try {
      const result = await getUsers(token);
      setUsers(Array.isArray(result) ? result : []);
    } catch (error) {
      toast.error(error.message || "Could not load users.");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { loadUsers(); }, [loadUsers]);

  const updateForm = (event) => {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }));
  };

  const createUser = async (event) => {
    event.preventDefault();
    setSaving(true);
    try {
      await createUserRequest({
          password: form.password,
          role: form.role,
          profile: {
            username: form.username,
            email: form.email,
            firstName: form.firstName,
            lastName: form.lastName,
            phone: form.phone || null,
          },
        }, token);
      setForm({ username: "", email: "", firstName: "", lastName: "", phone: "", password: "", role: "user" });
      toast.success("User created.");
      await loadUsers();
    } catch (error) {
      toast.error(error.message || "Could not create user.");
    } finally {
      setSaving(false);
    }
  };

  const changeRole = async (user, role) => {
    if (user.roles?.includes(role)) return;
    try {
      const updated = await updateUserRole(user.id, role, token);
      setUsers((current) => current.map((entry) => entry.id === updated.id ? updated : entry));
      toast.success(`Role changed to ${role}.`);
    } catch (error) {
      toast.error(error.message || "Could not change user role.");
    }
  };

  const changeStatus = async (user) => {
    const enabled = !user.enabled;
    setUpdatingIds((current) => [...current, user.id]);
    try {
      const updated = await updateUserStatus(user.id, enabled, token);
      setUsers((current) => current.map((entry) => entry.id === updated.id ? updated : entry));
      toast.success(enabled ? "User enabled." : "User disabled.");
    } catch (error) {
      toast.error(error.message || "Could not update user status.");
    } finally {
      setUpdatingIds((current) => current.filter((id) => id !== user.id));
    }
  };

  return (
    <section className="mt-8 border border-[#e4e6de] bg-white p-5 sm:p-6">
      <h2 className="text-lg font-medium text-[#354237]">User access</h2>
      <p className="mt-1 text-xs text-[#858a80]">Create accounts and assign admin, manager, or user access.</p>
      <form onSubmit={createUser} className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {[
          ["username", "Username", "text"],
          ["email", "Email", "email"],
          ["firstName", "First name", "text"],
          ["lastName", "Last name", "text"],
          ["phone", "Phone (optional)", "tel"],
          ["password", "Temporary password (min. 12 characters)", "password"],
        ].map(([name, label, type]) => (
          <label key={name} className="text-xs text-[#5d655a]">{label}
            <input name={name} type={type} required={name !== "phone"} minLength={name === "password" ? 12 : undefined}
              maxLength={name === "password" ? 128 : name === "email" ? 254 : 100} value={form[name]}
              onChange={updateForm} className="mt-1.5 h-10 w-full border border-[#dfe2d9] bg-white px-3 text-sm outline-none focus:border-[#71816d]" />
          </label>
        ))}
        <label className="text-xs text-[#5d655a]">Role
          <select name="role" value={form.role} onChange={updateForm} className="mt-1.5 h-10 w-full border border-[#dfe2d9] bg-white px-3 text-sm">
            {managedRoles.map((role) => <option key={role} value={role}>{role}</option>)}
          </select>
        </label>
        <button disabled={saving} className="h-10 self-end bg-[#314638] px-5 text-xs font-medium uppercase tracking-[0.1em] text-white disabled:opacity-60">
          {saving ? "Creating…" : "Create user"}
        </button>
      </form>

      <h3 className="mt-8 text-sm font-medium text-[#354237]">Existing users</h3>
      {loading ? <LoadingState label="Loading users…" compact /> : users.length === 0 ? (
        <p className="mt-3 text-sm text-[#858a80]">No users found.</p>
      ) : (
        <div className="mt-3 overflow-x-auto border border-[#e4e6de]">
          <table className="w-full min-w-[620px] text-left text-sm">
            <thead className="border-b border-[#e4e6de] bg-[#fbfbf8] text-xs text-[#858a80]">
              <tr><th className="px-4 py-3 font-medium">User</th><th className="px-4 py-3 font-medium">Email</th><th className="px-4 py-3 font-medium">Role</th><th className="px-4 py-3 font-medium">Status</th></tr>
            </thead>
            <tbody>{users.map((user) => (
              <tr key={user.id} className="border-b border-[#eef0ea] last:border-0">
                <td className="px-4 py-3 font-medium text-[#354237]">{user.firstName} {user.lastName} <span className="font-normal text-[#858a80]">(@{user.username})</span></td>
                <td className="px-4 py-3 text-[#687064]">{user.email}</td>
                <td className="px-4 py-3">
                  <select aria-label={`Role for ${user.username}`} value={user.roles?.find((role) => managedRoles.includes(role)) || "user"}
                    onChange={(event) => changeRole(user, event.target.value)} className="h-9 border border-[#dfe2d9] bg-white px-2 text-sm">
                    {managedRoles.map((role) => <option key={role} value={role}>{role}</option>)}
                  </select>
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    <span className={user.enabled ? "text-xs font-medium text-emerald-700" : "text-xs font-medium text-red-700"}>
                      {user.enabled ? "Enabled" : "Disabled"}
                    </span>
                    <button type="button" disabled={updatingIds.includes(user.id) || user.id === currentUserId}
                      onClick={() => changeStatus(user)} aria-label={`${user.enabled ? "Disable" : "Enable"} ${user.username}`}
                      className="rounded border border-[#dfe2d9] px-2.5 py-1 text-xs text-[#52644f] hover:bg-[#f4f5f0] disabled:cursor-not-allowed disabled:opacity-50">
                      {updatingIds.includes(user.id) ? "Saving…" : user.enabled ? "Disable" : "Enable"}
                    </button>
                  </div>
                </td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
    </section>
  );
}

export default function AdminDashboard({ managerMode = false, view = "overview" }) {
  const user = useSelector((state) => state.auth);
  const [products, setProducts] = useState([]);
  const [catalogTotal, setCatalogTotal] = useState(0);
  const [catalogPage, setCatalogPage] = useState(0);
  const [catalogPages, setCatalogPages] = useState(0);
  const [catalogQuery, setCatalogQuery] = useState("");
  const [catalogSearchInput, setCatalogSearchInput] = useState("");
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [catalogRefresh, setCatalogRefresh] = useState(0);
  const [users, setUsers] = useState([]);
  const [orderTotal, setOrderTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [banner, setBanner] = useState({
    enabled: true,
    eyebrow: "A more thoughtful everyday",
    heading: "Good things,",
    accentText: "well chosen.",
    description: "Useful, beautiful essentials made with intention. A little less, a lot better.",
    imageUrl: "https://images.unsplash.com/photo-1490312278390-ab64016e0aa9?auto=format&fit=crop&w=1500&q=90",
    imageAlt: "A calm, light-filled home with considered everyday objects",
    buttonText: "Explore the collection",
  });
  const [bannerLoading, setBannerLoading] = useState(true);
  const [savingBanner, setSavingBanner] = useState(false);
  const [productImagePreviewError, setProductImagePreviewError] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null);
  const [lastUpdatedProductId, setLastUpdatedProductId] = useState(null);
  const { register, handleSubmit, reset, control, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(productSchema),
    defaultValues: { name: "", category: "", productType: "", baseColor: "", description: "", price: "", discount: 0, imageUrl: "", stockCount: 0 },
  });
  const [previewName, previewCategory, previewColor, previewPrice, previewImageUrl] = useWatch({
    control,
    name: ["name", "category", "baseColor", "price", "imageUrl"],
  });

  useEffect(() => {
    setProductImagePreviewError(false);
  }, [previewImageUrl]);

  const loadDashboard = useCallback(async () => {
    setLoading(true);
    try {
      if (view === "overview") {
        const [userResult, orderResult, productResult] = await Promise.all([
          managerMode ? Promise.resolve([]) : getUsers(user.token),
          getOrders(0, 1, user.token),
          getProducts("page=0&size=1", user.token),
        ]);
        setUsers(Array.isArray(userResult) ? userResult : []);
        setOrderTotal(orderResult?.totalElements || 0);
        setCatalogTotal(productResult?.totalElements || 0);
      }
    } catch (error) {
      toast.error(error.message || "Could not load the admin dashboard.");
    } finally {
      setLoading(false);
    }
  }, [managerMode, user.token, view]);

  useEffect(() => {
    if (view === "overview") loadDashboard();
  }, [loadDashboard, view]);

  useEffect(() => {
    if (view !== "products") return undefined;
    let active = true;
    const params = new URLSearchParams({
      page: String(catalogPage),
      size: String(CATALOG_PAGE_SIZE),
      sortBy: "createdAt",
      direction: "desc",
    });
    if (catalogQuery) params.set("search", catalogQuery);

    setCatalogLoading(true);
    getProducts(params.toString(), user.token)
      .then((result) => {
        if (!active) return;
        const rows = Array.isArray(result) ? result : result.content || [];
        setProducts(rows.map(normalizeProduct));
        setCatalogTotal(Array.isArray(result) ? rows.length : result.totalElements || 0);
        setCatalogPages(Array.isArray(result) ? 1 : result.totalPages || 0);
      })
      .catch((error) => {
        if (active) toast.error(error.message || "Could not load the product catalog.");
      })
      .finally(() => {
        if (active) setCatalogLoading(false);
      });

    return () => { active = false; };
  }, [catalogPage, catalogQuery, catalogRefresh, user.token, view]);

  useEffect(() => {
    if (view !== "banner" || managerMode) return undefined;
    let active = true;
    getBanner(user.token)
      .then((result) => {
        if (active && result) setBanner(result);
      })
      .catch((error) => {
        if (active) toast.error(error.message || "Could not load the storefront banner.");
      })
      .finally(() => {
        if (active) setBannerLoading(false);
      });
    return () => { active = false; };
  }, [managerMode, user.token, view]);

  const updateBannerField = (event) => {
    const { name, value, checked, type } = event.target;
    setBanner((current) => ({ ...current, [name]: type === "checkbox" ? checked : value }));
  };

  const saveBanner = async (event) => {
    event.preventDefault();
    setSavingBanner(true);
    try {
      const saved = await updateBanner(banner, user.token);
      setBanner(saved);
      toast.success(saved.enabled ? "Storefront banner updated." : "Storefront banner hidden.");
    } catch (error) {
      toast.error(error.message || "Could not save the storefront banner.");
    } finally {
      setSavingBanner(false);
    }
  };

  const saveProduct = async (values) => {
    const discount = values.discount;
    const offerPrice = Number((values.price * (1 - discount / 100)).toFixed(2));
    const variants = editingProduct?.variants?.length
      ? editingProduct.variants.map((variant, index) => index === 0
        ? {
          id: variant.id,
          color: values.baseColor,
          imageUrls: [values.imageUrl, ...(variant.imageUrls || []).slice(1)],
          stockCount: values.stockCount,
        }
        : {
          id: variant.id,
          color: variant.color,
          imageUrls: variant.imageUrls,
          stockCount: variant.stockCount,
        })
      : [{ color: values.baseColor, imageUrls: [values.imageUrl], stockCount: values.stockCount }];
    const productData = {
      name: values.name,
      description: values.description,
      category: values.category,
      productType: values.productType,
      baseColor: values.baseColor,
      price: values.price,
      discount,
      offerPrice,
      listDate: editingProduct?.listDate || new Date().toISOString().slice(0, 10),
      variants,
    };
    try {
      if (editingProduct) {
        await updateProductRequest(editingProduct.productCode || editingProduct.id, productData, user.token);
        setLastUpdatedProductId(editingProduct.id);
        toast.success("Product updated.");
      } else {
        await createProductRequest(productData, user.token);
        toast.success("Product created.");
      }
      setEditingProduct(null);
      reset();
      if (!editingProduct) setCatalogPage(0);
      setCatalogRefresh((current) => current + 1);
      if (view === "overview") await loadDashboard();
    } catch (error) {
      toast.error(error.message || "Could not create product.");
    }
  };

  const editProduct = (product) => {
    const variant = product.variants?.[0];
    setEditingProduct(product);
    setProductImagePreviewError(false);
    reset({
      name: product.name || "",
      category: product.category || "",
      productType: product.productType || "",
      baseColor: product.baseColor || variant?.color || "",
      description: product.description || "",
      price: Number(product.price || 0),
      discount: Number(product.discount || 0),
      imageUrl: variant?.imageUrls?.[0] || "",
      stockCount: variant?.stockCount || 0,
    });
    document.getElementById("product-editor")?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const cancelProductEdit = () => {
    setEditingProduct(null);
    reset({
      name: "", category: "", productType: "", baseColor: "", description: "",
      price: "", discount: 0, imageUrl: "", stockCount: 0,
    });
  };

  const searchCatalog = (event) => {
    event.preventDefault();
    setCatalogPage(0);
    setCatalogQuery(catalogSearchInput.trim());
  };

  const clearCatalogSearch = () => {
    setCatalogSearchInput("");
    setCatalogQuery("");
    setCatalogPage(0);
  };

  const deleteProduct = async (product) => {
    if (!window.confirm(`Delete "${product.name}" from the catalog?`)) return;
    try {
      await deleteProductRequest(product.productCode || product.id, user.token);
      if (products.length === 1 && catalogPage > 0) setCatalogPage((page) => page - 1);
      else setCatalogRefresh((current) => current + 1);
      toast.success("Product deleted.");
    } catch (error) {
      toast.error(error.message || "Could not delete product.");
    }
  };

  return (
    <div>
      <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-[#788174]">{managerMode ? "Manager workspace" : "Administration"}</p>
      <h1 className="mt-2 text-3xl font-normal tracking-[-0.04em] text-[#29382d]">
        {view === "overview" ? "Store overview" : view === "products" ? "Product management" : view === "orders" ? "Order management" : view === "users" ? "User management" : "Storefront banner"}
      </h1>
      {view === "overview" && <div className="mt-7 grid gap-4 sm:grid-cols-3">
        {[{ label: "Catalog products", count: catalogTotal, Icon: Package }, { label: "Registered users", count: users.length, Icon: Users }, { label: "Orders", count: orderTotal, Icon: Package }].map(({ label, count, Icon }) => (
          <div key={label} className="border border-[#e4e6de] bg-white p-5"><div className="flex items-center justify-between"><p className="text-xs text-[#858a80]">{label}</p><Icon size={16} className="text-[#71816d]" /></div><p className="mt-3 text-3xl font-medium text-[#354237]">{loading ? "—" : count}</p></div>
        ))}
      </div>}

      {view === "banner" && !managerMode && <section className="mt-8 border border-[#e4e6de] bg-white p-5 sm:p-6">
        <div className="flex items-center gap-2"><Image size={17} className="text-[#52644f]" /><h2 className="text-lg font-medium text-[#354237]">Storefront banner</h2></div>
        <p className="mt-1 text-xs text-[#858a80]">Edit the home page hero banner or turn it off for shoppers.</p>
        {bannerLoading ? <LoadingState label="Loading banner settings…" compact /> : (
          <form onSubmit={saveBanner} className="mt-5 grid gap-4 sm:grid-cols-2">
            <label className={`${fieldClass} flex items-center gap-3 sm:col-span-2`}>
              <input type="checkbox" name="enabled" checked={banner.enabled} onChange={updateBannerField} className="h-4 w-4 accent-[#314638]" />
              Show banner on storefront
            </label>
            {[
              ["eyebrow", "Small heading", "text"],
              ["heading", "Main heading", "text"],
              ["accentText", "Italic heading", "text"],
              ["buttonText", "Button text", "text"],
              ["imageUrl", "Banner image URL", "url"],
              ["imageAlt", "Image description (accessibility)", "text"],
            ].map(([name, label, type]) => (
              <label key={name} className={fieldClass}>{label}
                <input name={name} type={type} required maxLength={name === "imageAlt" ? 300 : name === "imageUrl" ? 2048 : name === "eyebrow" ? 100 : name === "buttonText" ? 80 : 160} value={banner[name]} onChange={updateBannerField} className={inputClass} />
              </label>
            ))}
            <label className={`${fieldClass} sm:col-span-2`}>Banner description
              <textarea name="description" rows="3" required maxLength={500} value={banner.description} onChange={updateBannerField} className="mt-1.5 w-full border border-[#dfe2d9] bg-white px-3 py-2 text-sm outline-none focus:border-[#71816d]" />
            </label>
            {banner.imageUrl && <div className="sm:col-span-2"><img src={banner.imageUrl} alt={banner.imageAlt} className="h-40 w-full object-cover sm:h-56" /></div>}
            <button type="submit" disabled={savingBanner} className="flex h-10 items-center justify-center gap-2 bg-[#314638] px-5 text-xs font-medium uppercase tracking-[0.1em] text-white hover:bg-[#25372b] disabled:cursor-wait disabled:opacity-60 sm:col-span-2">
              {savingBanner ? "Saving…" : "Save banner settings"}
            </button>
          </form>
        )}
      </section>}

      {view === "orders" && <OrderManagement />}

      {view === "products" && <StockImportPanel token={user.token} onImported={() => setCatalogRefresh((current) => current + 1)} />}

      {view === "products" && <section id="product-editor" className="mt-8 border border-[#e4e6de] bg-white p-5 sm:p-6">
        <div className="flex items-center gap-2"><Plus size={17} className="text-[#52644f]" /><h2 className="text-lg font-medium text-[#354237]">{editingProduct ? "Edit product" : "Add a product"}</h2></div>
        <form onSubmit={handleSubmit(saveProduct)} noValidate className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[
            ["name", "Product name", "text"], ["category", "Category", "text"], ["productType", "Product type", "text"],
            ["baseColor", "Color", "text"], ["price", "Price", "number"], ["discount", "Discount (%)", "number"],
            ["imageUrl", "Image URL", "url"], ["stockCount", "Stock count", "number"],
          ].map(([name, label, type]) => <label key={name} className={fieldClass}>{label}<input type={type} step={type === "number" && name !== "stockCount" && name !== "discount" ? "0.01" : "1"} min={name === "price" ? "0.01" : name === "discount" ? "0" : name === "stockCount" ? "0" : undefined} max={name === "discount" ? "100" : undefined} {...register(name, type === "number" ? { valueAsNumber: true } : {})} className={inputClass} />{errors[name] && <span className="mt-1 block text-xs text-red-700">{errors[name].message}</span>}</label>)}
          <label className={`${fieldClass} sm:col-span-2 lg:col-span-3`}>Description<textarea rows="2" {...register("description")} className="mt-1.5 w-full border border-[#dfe2d9] bg-white px-3 py-2 text-sm outline-none focus:border-[#71816d]" />{errors.description && <span className="mt-1 block text-xs text-red-700">{errors.description.message}</span>}</label>
          <div className="sm:col-span-2 lg:col-span-3">
            <p className="mb-2 text-xs font-medium text-[#5d655a]">Live product preview</p>
            <div className="flex max-w-xl gap-4 border border-[#e4e6de] bg-[#fbfbf8] p-3">
              <div className="h-28 w-24 shrink-0 overflow-hidden bg-[#eeefe9] sm:h-36 sm:w-32">
                {previewImageUrl ? (
                  productImagePreviewError
                    ? <div className="grid h-full place-items-center px-2 text-center text-[11px] text-[#92978e]">Image URL could not be loaded</div>
                    : <img src={previewImageUrl} alt={previewName || "Product image preview"} className="h-full w-full object-cover" onError={() => setProductImagePreviewError(true)} />
                ) : <div className="grid h-full place-items-center px-2 text-center text-[11px] text-[#92978e]">Image preview will appear here</div>}
              </div>
              <div className="min-w-0 py-1">
                <p className="text-[10px] uppercase tracking-[0.14em] text-[#858a80]">{previewCategory || "Category"}</p>
                <p className="mt-2 truncate text-sm font-medium text-[#354237]">{previewName || "Product name"}</p>
                <p className="mt-1 text-xs text-[#858a80]">{previewColor || "Color"}</p>
                <p className="mt-3 text-sm text-[#454d43]">{previewPrice ? `$${Number(previewPrice).toFixed(2)}` : "Price"}</p>
              </div>
            </div>
          </div>
          <div className="flex gap-3 sm:col-span-2 lg:col-span-3">
            <button disabled={isSubmitting} className="flex h-10 items-center justify-center gap-2 bg-[#314638] px-5 text-xs font-medium uppercase tracking-[0.1em] text-white hover:bg-[#25372b] disabled:opacity-60">{isSubmitting ? "Saving..." : editingProduct ? "Save changes" : "Create product"} <Plus size={14} /></button>
            {editingProduct && <button type="button" onClick={cancelProductEdit} className="flex h-10 items-center gap-2 border border-[#dfe2d9] px-4 text-xs text-[#52644f] hover:bg-[#f4f5f0]"><X size={14} /> Cancel</button>}
          </div>
        </form>
      </section>}

      {view === "products" && <section className="mt-8">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="text-lg font-medium text-[#354237]">Product catalog</h2>
            <p className="mt-1 text-xs text-[#858a80]">{catalogTotal.toLocaleString()} products · Showing {products.length} per page</p>
          </div>
          <form onSubmit={searchCatalog} className="flex w-full gap-2 sm:w-auto">
            <input type="search" value={catalogSearchInput} onChange={(event) => setCatalogSearchInput(event.target.value)}
              placeholder="Search products..." aria-label="Search products"
              className="h-10 min-w-0 flex-1 border border-[#dfe2d9] bg-white px-3 text-sm outline-none focus:border-[#71816d] sm:w-64" />
            <button type="submit" className="h-10 bg-[#314638] px-4 text-xs font-medium text-white hover:bg-[#25372b]">Search</button>
            {catalogQuery && <button type="button" onClick={clearCatalogSearch} className="h-10 border border-[#dfe2d9] px-3 text-xs text-[#52644f] hover:bg-[#f4f5f0]">Clear</button>}
          </form>
        </div>
        {catalogPages > 1 && <div className="mt-4 flex items-center justify-between border border-[#e4e6de] bg-white px-4 py-3">
          <button type="button" disabled={catalogPage === 0 || catalogLoading} onClick={() => setCatalogPage((page) => page - 1)}
            className="rounded border border-[#dfe2d9] px-3 py-2 text-xs text-[#52644f] disabled:opacity-40">Previous</button>
          <span className="text-xs text-[#687064]">Page {catalogPage + 1} of {catalogPages}</span>
          <button type="button" disabled={catalogPage + 1 >= catalogPages || catalogLoading} onClick={() => setCatalogPage((page) => page + 1)}
            className="rounded border border-[#dfe2d9] px-3 py-2 text-xs text-[#52644f] disabled:opacity-40">Next</button>
        </div>}
        {catalogLoading ? <LoadingState label="Loading product catalog…" compact /> : products.length === 0 ? <p className="mt-4 border border-[#e4e6de] bg-white p-6 text-sm text-[#858a80]">{catalogQuery ? `No products match “${catalogQuery}”.` : "No products found."}</p> : (
          <div className="mt-4 overflow-x-auto border border-[#e4e6de] bg-white">
            <table className="w-full min-w-[620px] text-left text-sm">
              <thead className="border-b border-[#e4e6de] bg-[#fbfbf8] text-xs text-[#858a80]"><tr><th className="px-4 py-3 font-medium">Product / code</th><th className="px-4 py-3 font-medium">Category</th><th className="px-4 py-3 font-medium">Price</th><th className="px-4 py-3 font-medium">Stock</th>{!managerMode && <th className="px-4 py-3 text-right font-medium">Action</th>}</tr></thead>
              <tbody>{products.map((product) => <tr key={product.id} className="border-b border-[#eef0ea] last:border-0"><td className="px-4 py-3 font-medium text-[#354237]"><span className="inline-flex items-center gap-2">{product.name}{product.id === lastUpdatedProductId && <span title="Updated successfully" aria-label="Updated successfully" className="h-2 w-2 rounded-full bg-emerald-600" />}</span><span className="mt-1 block font-mono text-[11px] font-normal text-[#858a80]">{product.productCode || "Code pending"}</span></td><td className="px-4 py-3 text-[#687064]">{product.category}</td><td className="px-4 py-3 text-[#687064]">${Number(product.offerPrice ?? product.price).toFixed(2)}</td><td className="px-4 py-3 text-[#687064]">{product.stockCount ?? "—"}</td>{!managerMode && <td className="px-4 py-3 text-right"><button onClick={() => editProduct(product)} aria-label={`Edit ${product.name}`} className="rounded p-2 text-[#71816d] hover:bg-[#eef1e9]"><Pencil size={15} /></button><button onClick={() => deleteProduct(product)} aria-label={`Delete ${product.name}`} className="rounded p-2 text-[#92978e] hover:bg-red-50 hover:text-red-700"><Trash2 size={15} /></button></td>}</tr>)}</tbody>
            </table>
          </div>
        )}
        {catalogPages > 1 && <div className="mt-3 flex items-center justify-between">
          <button type="button" disabled={catalogPage === 0 || catalogLoading} onClick={() => setCatalogPage((page) => page - 1)}
            className="rounded border border-[#dfe2d9] bg-white px-3 py-2 text-xs text-[#52644f] disabled:opacity-40">Previous</button>
          <span className="text-xs text-[#687064]">Page {catalogPage + 1} of {catalogPages}</span>
          <button type="button" disabled={catalogPage + 1 >= catalogPages || catalogLoading} onClick={() => setCatalogPage((page) => page + 1)}
            className="rounded border border-[#dfe2d9] bg-white px-3 py-2 text-xs text-[#52644f] disabled:opacity-40">Next</button>
        </div>}
      </section>}
      {view === "users" && !managerMode && <UserManagement />}
    </div>
  );
}
