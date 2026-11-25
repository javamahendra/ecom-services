import { RefreshCw, Search } from "lucide-react";
import { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { toast } from "react-toastify";
import LoadingState from "../../components/LoadingState";
import {
  getInventory,
  syncCatalogInventory,
  syncCatalogMetadata,
  syncInventoryVariantToProduct,
  updateInventory,
} from "../../services/inventoryService";

const PAGE_SIZE = 20;

export default function InventoryManagement() {
  const token = useSelector((state) => state.auth.token);
  const [items, setItems] = useState([]);
  const [quantities, setQuantities] = useState({});
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState(null);
  const [syncingId, setSyncingId] = useState(null);
  const [syncing, setSyncing] = useState(false);
  const [refreshingMetadata, setRefreshingMetadata] = useState(false);
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [colorInput, setColorInput] = useState("");
  const [minQuantityInput, setMinQuantityInput] = useState("");
  const [maxQuantityInput, setMaxQuantityInput] = useState("");
  const [quantityFilterEnabled, setQuantityFilterEnabled] = useState(false);
  const [quantityOperatorInput, setQuantityOperatorInput] = useState("lte");
  const [quantityValueInput, setQuantityValueInput] = useState("10");
  const [fromDateInput, setFromDateInput] = useState("");
  const [toDateInput, setToDateInput] = useState("");
  const [filters, setFilters] = useState({});
  const [sortBy, setSortBy] = useState("updatedAt");
  const [direction, setDirection] = useState("desc");
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    let active = true;
    const params = new URLSearchParams({
      page: String(page),
      size: String(PAGE_SIZE),
      sortBy,
      direction,
    });
    if (search) params.set("search", search);
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== "") params.set(key, value);
    });
    setLoading(true);
    getInventory(params.toString(), token)
      .then((result) => {
        if (!active) return;
        const content = result?.content || [];
        setItems(content);
        setQuantities(Object.fromEntries(content.map((item) => [item.id, item.quantity])));
        setPages(result?.totalPages || 0);
        setTotal(result?.totalElements || 0);
      })
      .catch((error) => {
        if (active) toast.error(error.message || "Could not load inventory.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [page, refresh, search, filters, sortBy, direction, token]);

  const submitSearch = (event) => {
    event.preventDefault();
    if (quantityFilterEnabled
      && (!Number.isInteger(Number(quantityValueInput)) || Number(quantityValueInput) < 0)) {
      toast.error("Quantity comparison value must be a non-negative whole number.");
      return;
    }
    setPage(0);
    setSearch(searchInput.trim());
    const nextFilters = {
      minQuantity: minQuantityInput,
      maxQuantity: maxQuantityInput,
      fromDate: fromDateInput,
      toDate: toDateInput,
      color: colorInput.trim(),
    };
    if (quantityFilterEnabled) {
      nextFilters.quantityOperator = quantityOperatorInput;
      nextFilters.quantityValue = quantityValueInput;
    }
    setFilters(nextFilters);
  };

  const clearFilters = () => {
    setSearchInput("");
    setColorInput("");
    setMinQuantityInput("");
    setMaxQuantityInput("");
    setQuantityFilterEnabled(false);
    setQuantityOperatorInput("lte");
    setQuantityValueInput("10");
    setFromDateInput("");
    setToDateInput("");
    setSearch("");
    setFilters({});
    setPage(0);
  };

  const saveQuantity = async (item) => {
    const quantity = Number(quantities[item.id]);
    if (!Number.isInteger(quantity) || quantity < 0) {
      toast.error("Stock quantity must be a non-negative whole number.");
      return;
    }
    setSavingId(item.id);
    try {
      await updateInventory({
        productId: item.productId,
        variantId: item.variantId,
        productName: item.productName,
        color: item.color,
        quantity,
      }, token);
      toast.success("Inventory updated. The product catalog will reflect the change shortly.");
      setRefresh((current) => current + 1);
    } catch (error) {
      toast.error(error.message || "Could not update inventory.");
    } finally {
      setSavingId(null);
    }
  };

  const syncVariantToProduct = async (item) => {
    setSyncingId(item.id);
    try {
      await syncInventoryVariantToProduct(item.productId, item.variantId, token);
      toast.success("Inventory stock was sent to the product catalog. It will update shortly.");
    } catch (error) {
      toast.error(error.message || "Could not update product stock.");
    } finally {
      setSyncingId(null);
    }
  };

  const syncCatalog = async () => {
    setSyncing(true);
    try {
      const result = await syncCatalogInventory(token);
      toast.success(`${result?.syncedVariants || 0} catalog variants queued to update inventory quantities. Refresh shortly to see changes.`);
    } catch (error) {
      toast.error(error.message || "Could not sync catalog stock to inventory.");
    } finally {
      setSyncing(false);
    }
  };

  const refreshCatalogMetadata = async () => {
    setRefreshingMetadata(true);
    try {
      const result = await syncCatalogMetadata(token);
      toast.success(`${result?.syncedVariants || 0} variants queued to refresh product names and codes without changing stock.`);
    } catch (error) {
      toast.error(error.message || "Could not refresh product details.");
    } finally {
      setRefreshingMetadata(false);
    }
  };

  return (
    <div>
      <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-[#788174]">Stock control</p>
      <h1 className="mt-2 text-3xl font-normal tracking-[-0.04em] text-[#29382d]">Inventory</h1>
      <section className="mt-8 border border-[#e4e6de] bg-white p-5 sm:p-6">
        <p className="mb-4 border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
          Updating from product stock replaces current available inventory quantities. Run it only when orders are not being processed.
        </p>
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h2 className="text-lg font-medium text-[#354237]">Inventory by product variant</h2>
            <p className="mt-1 text-xs text-[#858a80]">{total.toLocaleString()} variants · {PAGE_SIZE} per page</p>
          </div>
          <div className="flex w-full flex-wrap items-end gap-2">
            <form onSubmit={submitSearch} className="flex flex-wrap items-end gap-2">
            <label className="grid gap-1 text-[10px] text-[#788174]">Product name/code or variant ID
              <input type="search" value={searchInput} onChange={(event) => setSearchInput(event.target.value)}
                placeholder="Name, DAJLFYMLAZ, variant ID..." aria-label="Search inventory"
                className="h-10 min-w-0 border border-[#dfe2d9] px-3 text-sm outline-none focus:border-[#71816d] sm:w-56" />
            </label>
            <label className="grid gap-1 text-[10px] text-[#788174]">Min quantity
              <input type="number" min="0" value={minQuantityInput} onChange={(event) => setMinQuantityInput(event.target.value)}
                className="h-10 w-28 border border-[#dfe2d9] px-3 text-sm" />
            </label>
            <label className="grid gap-1 text-[10px] text-[#788174]">Variant color
              <input type="search" value={colorInput} onChange={(event) => setColorInput(event.target.value)}
                className="h-10 w-32 border border-[#dfe2d9] px-3 text-sm" />
            </label>
            <label className="grid gap-1 text-[10px] text-[#788174]">Max quantity
              <input type="number" min="0" value={maxQuantityInput} onChange={(event) => setMaxQuantityInput(event.target.value)}
                className="h-10 w-28 border border-[#dfe2d9] px-3 text-sm" />
            </label>
            <label className="flex h-10 items-center gap-2 border border-[#dfe2d9] px-2 text-xs text-[#52644f]">
              <input type="checkbox" checked={quantityFilterEnabled}
                onChange={(event) => setQuantityFilterEnabled(event.target.checked)}
                className="accent-[#314638]" />
              Quantity
              <select value={quantityOperatorInput} disabled={!quantityFilterEnabled}
                onChange={(event) => setQuantityOperatorInput(event.target.value)}
                aria-label="Quantity comparison operator"
                className="h-7 border border-[#dfe2d9] bg-white px-1 disabled:bg-[#f4f5f0]">
                <option value="eq">=</option><option value="gte">≥</option><option value="lte">≤</option>
                <option value="gt">&gt;</option><option value="lt">&lt;</option><option value="ne">≠</option>
              </select>
              <input type="number" min="0" step="1" value={quantityValueInput}
                disabled={!quantityFilterEnabled}
                onChange={(event) => setQuantityValueInput(event.target.value)}
                aria-label="Quantity comparison value (default 10)"
                className="h-7 w-14 border border-[#dfe2d9] px-1 disabled:bg-[#f4f5f0]" />
            </label>
            <label className="grid gap-1 text-[10px] text-[#788174]">Updated from
              <input type="date" value={fromDateInput} onChange={(event) => setFromDateInput(event.target.value)}
                className="h-10 border border-[#dfe2d9] px-2 text-sm" />
            </label>
            <label className="grid gap-1 text-[10px] text-[#788174]">Updated to
              <input type="date" value={toDateInput} onChange={(event) => setToDateInput(event.target.value)}
                className="h-10 border border-[#dfe2d9] px-2 text-sm" />
            </label>
            <button type="submit" aria-label="Apply inventory filters" className="grid h-10 w-10 place-items-center bg-[#314638] text-white hover:bg-[#25372b]"><Search size={16} /></button>
            <button type="button" onClick={clearFilters}
              className="h-10 border border-[#dfe2d9] px-3 text-xs text-[#52644f] hover:bg-[#f4f5f0]">Clear</button>
            </form>
            <label className="grid gap-1 text-[10px] text-[#788174]">Sort inventory by
              <select value={sortBy} onChange={(event) => { setSortBy(event.target.value); setPage(0); }}
                className="h-10 border border-[#dfe2d9] bg-white px-2 text-sm">
                <option value="updatedAt">Last updated</option>
                <option value="productCode">Product code</option>
                <option value="productName">Product name</option>
                <option value="color">Variant color</option>
                <option value="quantity">Quantity</option>
                <option value="variantId">Variant ID</option>
              </select>
            </label>
            <select aria-label="Inventory sort direction" value={direction}
              onChange={(event) => { setDirection(event.target.value); setPage(0); }}
              className="h-10 border border-[#dfe2d9] bg-white px-2 text-sm">
              <option value="asc">Ascending</option>
              <option value="desc">Descending</option>
            </select>
            <button type="button" onClick={() => setRefresh((current) => current + 1)} disabled={loading}
              className="inline-flex h-10 items-center gap-2 border border-[#dfe2d9] px-3 text-xs text-[#52644f] hover:bg-[#f4f5f0] disabled:opacity-50">
              <RefreshCw size={14} /> Refresh
            </button>
            <button type="button" onClick={syncCatalog} disabled={syncing}
              className="h-10 bg-[#314638] px-4 text-xs font-medium text-white hover:bg-[#25372b] disabled:opacity-60">
              {syncing ? "Syncing…" : "Update from product stock"}
            </button>
            <button type="button" onClick={refreshCatalogMetadata} disabled={refreshingMetadata}
              className="h-10 border border-[#dfe2d9] px-3 text-xs text-[#52644f] hover:bg-[#f4f5f0] disabled:opacity-50">
              {refreshingMetadata ? "Refreshing details…" : "Refresh product codes"}
            </button>
          </div>
        </div>

        {loading ? <LoadingState label="Loading inventory…" compact /> : items.length === 0 ? (
          <p className="mt-4 border border-[#e4e6de] p-5 text-sm text-[#858a80]">
            No inventory records found. Use “Update from product stock” to create inventory records from existing product variants.
          </p>
        ) : (
          <div className="mt-4 overflow-x-auto border border-[#e4e6de]">
            <table className="w-full min-w-[920px] text-left text-sm">
              <thead className="border-b border-[#e4e6de] bg-[#fbfbf8] text-xs text-[#858a80]">
                <tr><th className="px-4 py-3 font-medium">Product</th><th className="px-4 py-3 font-medium">Variant</th><th className="px-4 py-3 font-medium">Available stock</th><th className="px-4 py-3 font-medium">Last updated</th><th className="px-4 py-3 text-right font-medium">Actions</th></tr>
              </thead>
              <tbody>{items.map((item) => (
                <tr key={item.id} className="border-b border-[#eef0ea] last:border-0">
                  <td className="px-4 py-3 font-medium text-[#354237]">{item.productName}<span className="mt-1 block font-mono text-[10px] font-normal text-[#858a80]">Product code: {item.productCode || "Not synced yet"}</span></td>
                  <td className="px-4 py-3 text-[#687064]">{item.color}<span className="mt-1 block font-mono text-[10px] text-[#858a80]">{item.variantId}</span></td>
                  <td className="px-4 py-3">
                    <input type="number" min="0" step="1" value={quantities[item.id] ?? ""}
                      aria-label={`Available stock for ${item.productName} ${item.color}`}
                      onChange={(event) => setQuantities((current) => ({ ...current, [item.id]: event.target.value }))}
                      className="h-9 w-24 border border-[#dfe2d9] px-2 text-sm" />
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-[#687064]">{item.updatedAt ? new Date(item.updatedAt).toLocaleString() : "—"}</td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex justify-end gap-2">
                      <button type="button" disabled={savingId === item.id || Number(quantities[item.id]) === item.quantity}
                        onClick={() => saveQuantity(item)}
                        className="h-9 bg-[#314638] px-3 text-xs text-white hover:bg-[#25372b] disabled:cursor-not-allowed disabled:opacity-40">
                        {savingId === item.id ? "Saving…" : "Save stock"}
                      </button>
                      <button type="button" disabled={syncingId === item.id || Number(quantities[item.id]) !== item.quantity}
                        onClick={() => syncVariantToProduct(item)}
                        className="h-9 border border-[#dfe2d9] px-3 text-xs text-[#52644f] hover:bg-[#f4f5f0] disabled:opacity-50">
                        {syncingId === item.id ? "Sending…" : "Update product stock"}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}</tbody>
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
    </div>
  );
}
