import { ArrowDown, ChevronDown, ChevronUp, Image, Package, Pencil, Plus, Search, Trash2, Users, X } from "lucide-react";
import { Fragment, useCallback, useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { toast } from "react-toastify";
import { Link } from "react-router-dom";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { normalizeProduct } from "../../api";
import LoadingState from "../../components/LoadingState";
import { getBanner, updateBanner } from "../../services/bannerService";
import { createProduct as createProductRequest, deleteProduct as deleteProductRequest, getProductCount, getProducts, updateProduct as updateProductRequest } from "../../services/productService";
import { getUserCount } from "../../services/userService";
import { getDispatchCount, getOrderCount } from "../../services/orderService";
import { DispatchManagement } from "./components/DispatchManagement";
import { OrderManagement } from "./components/OrderManagement";
import { StockImportPanel } from "./components/StockImportPanel";
import { UserManagement } from "./components/UserManagement";

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
  variantStocks: z.array(z.number({ error: "Enter a valid stock quantity." }).int().min(0)).optional(),
});

const inputClass = "mt-1.5 h-10 w-full border border-[#dfe2d9] bg-white px-3 text-sm outline-none focus:border-[#71816d]";
const fieldClass = "block text-xs text-[#5d655a]";

const CATALOG_PAGE_SIZE = 20;




export default function AdminDashboard({ managerMode = false, view = "overview" }) {
  const user = useSelector((state) => state.auth);
  const [products, setProducts] = useState([]);
  const [catalogTotal, setCatalogTotal] = useState(0);
  const [catalogPage, setCatalogPage] = useState(0);
  const [catalogPages, setCatalogPages] = useState(0);
  const [catalogQuery, setCatalogQuery] = useState("");
  const [catalogSearchInput, setCatalogSearchInput] = useState("");
  const [catalogSortBy, setCatalogSortBy] = useState("createdAt");
  const [catalogDirection, setCatalogDirection] = useState("desc");
  const [catalogFilterInputs, setCatalogFilterInputs] = useState({
    productType: "",
    minPrice: "",
    maxPrice: "",
    minDiscount: "",
    maxDiscount: "",
    colorCount: "",
    stockFilterEnabled: false,
    stockOperator: "lte",
    stockValue: "10",
  });
  const [catalogFilters, setCatalogFilters] = useState({});
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [catalogRefresh, setCatalogRefresh] = useState(0);
  const [overviewCounts, setOverviewCounts] = useState({
    users: null,
    orders: null,
    dispatches: null,
    products: null,
  });
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
  const [lastUpdatedProduct, setLastUpdatedProduct] = useState(null);
  const [focusProductId, setFocusProductId] = useState(null);
  const [expandedProductId, setExpandedProductId] = useState(null);
  const { register, handleSubmit, reset, control, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(productSchema),
    defaultValues: {
      name: "", category: "", productType: "", baseColor: "", description: "",
      price: "", discount: 0, imageUrl: "", stockCount: 0, variantStocks: [],
    },
  });
  const [previewName, previewCategory, previewColor, previewPrice, previewImageUrl, variantStocks] = useWatch({
    control,
    name: ["name", "category", "baseColor", "price", "imageUrl", "variantStocks"],
  });

  useEffect(() => {
    setProductImagePreviewError(false);
  }, [previewImageUrl]);

  const loadDashboard = useCallback(async () => {
    if (view !== "overview") return;
    setOverviewCounts({ users: null, orders: null, dispatches: null, products: null });
    const loadCount = async (key, label, request) => {
      try {
        const count = await request();
        setOverviewCounts((current) => ({ ...current, [key]: count }));
      } catch (error) {
        toast.error(`Could not load ${label.toLowerCase()} count: ${error.message || "request failed."}`);
      }
    };
    await Promise.all([
      ...(managerMode ? [] : [loadCount("users", "Registered users", () => getUserCount(user.token))]),
      loadCount("orders", "Orders", () => getOrderCount(user.token)),
      loadCount("dispatches", "Dispatches", () => getDispatchCount(user.token)),
      loadCount("products", "Catalog products", () => getProductCount(user.token)),
    ]);
  }, [managerMode, user.token, view]);

  useEffect(() => {
    if (view === "overview") loadDashboard();
  }, [loadDashboard, view]);

  useEffect(() => {
    if (!focusProductId || catalogLoading) return;
    const row = document.getElementById(`product-row-${focusProductId}`);
    if (row) {
      row.scrollIntoView({ behavior: "smooth", block: "center" });
      setFocusProductId(null);
    }
  }, [catalogLoading, focusProductId, products]);

  useEffect(() => {
    if (view !== "products") return undefined;
    let active = true;
    const params = new URLSearchParams({
      page: String(catalogPage),
      size: String(CATALOG_PAGE_SIZE),
      sortBy: catalogSortBy,
      direction: catalogDirection,
    });
    if (catalogQuery) params.set("search", catalogQuery);
    Object.entries(catalogFilters).forEach(([key, value]) => {
      if (value !== "" && value !== undefined) params.set(key, String(value));
    });

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
  }, [catalogDirection, catalogFilters, catalogPage, catalogQuery, catalogRefresh, catalogSortBy, user.token, view]);

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
      ? editingProduct.variants.map((variant, index) => ({
          id: variant.id,
          color: index === 0 ? values.baseColor : variant.color,
          imageUrls: index === 0
            ? [values.imageUrl, ...(variant.imageUrls || []).slice(1)]
            : variant.imageUrls,
          stockCount: values.variantStocks?.[index] ?? variant.stockCount,
        }))
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
        const updatedProduct = await updateProductRequest(
          editingProduct.productCode || editingProduct.id, productData, user.token);
        setLastUpdatedProduct({
          id: updatedProduct?.id || editingProduct.id,
          productCode: updatedProduct?.productCode || editingProduct.productCode,
          name: updatedProduct?.name || values.name,
        });
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
      variantStocks: (product.variants || []).map((item) => item.stockCount || 0),
    });
    document.getElementById("product-editor")?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const cancelProductEdit = () => {
    setEditingProduct(null);
    reset({
      name: "", category: "", productType: "", baseColor: "", description: "",
      price: "", discount: 0, imageUrl: "", stockCount: 0, variantStocks: [],
    });
  };

  const clearCatalogSearch = () => {
    setCatalogSearchInput("");
    setCatalogQuery("");
    setCatalogFilters({});
    setCatalogFilterInputs({
      productType: "",
      minPrice: "",
      maxPrice: "",
      minDiscount: "",
      maxDiscount: "",
      colorCount: "",
      stockFilterEnabled: false,
      stockOperator: "lte",
      stockValue: "10",
    });
    setCatalogPage(0);
  };

  const applyCatalogFilters = (event) => {
    event.preventDefault();
    const { stockFilterEnabled, stockOperator, stockValue, ...filters } = catalogFilterInputs;
    if (stockFilterEnabled && (!Number.isInteger(Number(stockValue)) || Number(stockValue) < 0)) {
      toast.error("Stock comparison value must be a non-negative whole number.");
      return;
    }
    if (catalogFilterInputs.colorCount !== ""
        && (!Number.isInteger(Number(catalogFilterInputs.colorCount)) || Number(catalogFilterInputs.colorCount) < 0)) {
      toast.error("Color count must be a non-negative whole number.");
      return;
    }
    if (stockFilterEnabled) {
      filters.stockOperator = stockOperator;
      filters.stockValue = stockValue;
    }
    setCatalogPage(0);
    setCatalogQuery(catalogSearchInput.trim());
    setCatalogFilters(filters);
  };

  const goToUpdatedProduct = () => {
    if (!lastUpdatedProduct?.productCode) {
      toast.error("The updated product has no product code to search.");
      return;
    }
    setCatalogSearchInput(lastUpdatedProduct.productCode);
    setCatalogQuery(lastUpdatedProduct.productCode);
    setCatalogFilters({});
    setCatalogFilterInputs({
      productType: "",
      minPrice: "",
      maxPrice: "",
      minDiscount: "",
      maxDiscount: "",
      colorCount: "",
      stockFilterEnabled: false,
      stockOperator: "lte",
      stockValue: "10",
    });
    setCatalogPage(0);
    setFocusProductId(lastUpdatedProduct.id);
    document.getElementById("product-catalog")?.scrollIntoView({ behavior: "smooth", block: "start" });
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
        {view === "overview" ? "Store overview" : view === "products" ? "Product management" : view === "orders" ? "Order management" : view === "dispatches" ? "Dispatch management" : view === "users" ? "User management" : "Storefront banner"}
      </h1>
      {view === "overview" && <div className="mt-7 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {[
          { label: "Catalog products", count: overviewCounts.products, Icon: Package, to: managerMode ? "/manager/products" : "/admin/products" },
          ...(!managerMode ? [{ label: "Registered users", count: overviewCounts.users, Icon: Users, to: "/admin/users" }] : []),
          { label: "Orders", count: overviewCounts.orders, Icon: Package, to: managerMode ? "/manager/orders" : "/admin/orders" },
          { label: "Dispatches", count: overviewCounts.dispatches, Icon: Package, to: managerMode ? "/manager/dispatches" : "/admin/dispatches" },
        ].map(({ label, count, Icon, to }) => (
          <Link key={label} to={to} className="border border-[#e4e6de] bg-white p-5 transition-colors hover:border-[#9ca995] hover:bg-[#fbfbf8] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#52644f]">
            <div className="flex items-center justify-between"><p className="text-xs text-[#858a80]">{label}</p><Icon size={16} className="text-[#71816d]" /></div>
            <p className="mt-3 text-3xl font-medium text-[#354237]">{count ?? "—"}</p>
          </Link>
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

      {view === "dispatches" && <DispatchManagement />}

      {view === "products" && <StockImportPanel token={user.token} onImported={() => setCatalogRefresh((current) => current + 1)} />}

      {view === "products" && <section id="product-editor" className="mt-8 border border-[#e4e6de] bg-white p-5 sm:p-6">
        <div className="flex items-center gap-2"><Plus size={17} className="text-[#52644f]" /><h2 className="text-lg font-medium text-[#354237]">{editingProduct ? "Edit product" : "Add a product"}</h2></div>
        <form onSubmit={handleSubmit(saveProduct)} noValidate className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[
            ["name", "Product name", "text"], ["category", "Category", "text"], ["productType", "Product type", "text"],
            ["baseColor", "Color", "text"], ["price", "Price", "number"], ["discount", "Discount (%)", "number"],
            ["imageUrl", "Image URL", "url"],
            ...(!editingProduct ? [["stockCount", "Stock count", "number"]] : []),
          ].map(([name, label, type]) => <label key={name} className={fieldClass}>{label}<input type={type} step={type === "number" && name !== "stockCount" && name !== "discount" ? "0.01" : "1"} min={name === "price" ? "0.01" : name === "discount" ? "0" : name === "stockCount" ? "0" : undefined} max={name === "discount" ? "100" : undefined} {...register(name, type === "number" ? { valueAsNumber: true } : {})} className={inputClass} />{errors[name] && <span className="mt-1 block text-xs text-red-700">{errors[name].message}</span>}</label>)}
          {editingProduct?.variants?.length > 0 && <div className="sm:col-span-2 lg:col-span-3">
            <h3 className="text-sm font-medium text-[#354237]">Stock by variant</h3>
            <p className="mt-1 text-xs text-[#858a80]">
              Catalog total: {(variantStocks || []).reduce((total, quantity) =>
                total + (Number.isFinite(Number(quantity)) ? Number(quantity) : 0), 0)} units. Set stock separately for each variant.
            </p>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              {editingProduct.variants.map((variant, index) => (
                <label key={variant.id} className="flex items-center justify-between gap-3 border border-[#e4e6de] p-3 text-xs text-[#5d655a]">
                  <span>{variant.color}<span className="mt-1 block font-mono text-[10px] text-[#858a80]">{variant.id}</span></span>
                  <span className="shrink-0">Stock
                    <input type="number" min="0" step="1" {...register(`variantStocks.${index}`, { valueAsNumber: true })}
                      aria-label={`Stock for ${variant.color} variant`} className="ml-2 h-9 w-20 border border-[#dfe2d9] px-2 text-sm" />
                  </span>
                </label>
              ))}
            </div>
          </div>}
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
        {lastUpdatedProduct && <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border border-emerald-200 bg-emerald-50 px-4 py-3">
          <p className="text-sm text-emerald-900">
            Updated: <span className="font-medium">{lastUpdatedProduct.name}</span>
            <span className="ml-2 font-mono text-xs">{lastUpdatedProduct.productCode}</span>
          </p>
          <button type="button" onClick={goToUpdatedProduct}
            className="inline-flex h-9 items-center gap-2 bg-[#314638] px-3 text-xs font-medium text-white hover:bg-[#25372b]">
            <ArrowDown size={14} /> Go to updated product
          </button>
        </div>}
      </section>}

      {view === "products" && <section id="product-catalog" className="mt-8">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="text-lg font-medium text-[#354237]">Product catalog</h2>
            <p className="mt-1 text-xs text-[#858a80]">{catalogTotal.toLocaleString()} products · Showing {products.length} per page</p>
          </div>
          <div className="flex w-full flex-wrap items-end gap-2 sm:w-auto">
          <form onSubmit={applyCatalogFilters} className="flex w-full flex-wrap items-end gap-2">
            <input type="search" value={catalogSearchInput} onChange={(event) => setCatalogSearchInput(event.target.value)}
              placeholder="Search products..." aria-label="Search products"
              className="h-10 min-w-0 flex-1 border border-[#dfe2d9] bg-white px-3 text-sm outline-none focus:border-[#71816d] sm:w-56" />
            <input type="search" value={catalogFilterInputs.productType}
              onChange={(event) => setCatalogFilterInputs((current) => ({ ...current, productType: event.target.value }))}
              placeholder="Product type" aria-label="Filter by product type"
              className="h-10 w-36 border border-[#dfe2d9] bg-white px-3 text-sm" />
            <input type="number" min="0" step="0.01" value={catalogFilterInputs.minPrice}
              onChange={(event) => setCatalogFilterInputs((current) => ({ ...current, minPrice: event.target.value }))}
              placeholder="Min price" aria-label="Minimum product price"
              className="h-10 w-28 border border-[#dfe2d9] bg-white px-2 text-sm" />
            <input type="number" min="0" step="0.01" value={catalogFilterInputs.maxPrice}
              onChange={(event) => setCatalogFilterInputs((current) => ({ ...current, maxPrice: event.target.value }))}
              placeholder="Max price" aria-label="Maximum product price"
              className="h-10 w-28 border border-[#dfe2d9] bg-white px-2 text-sm" />
            <input type="number" min="0" max="100" step="1" value={catalogFilterInputs.minDiscount}
              onChange={(event) => setCatalogFilterInputs((current) => ({ ...current, minDiscount: event.target.value }))}
              placeholder="Min discount %" aria-label="Minimum discount percentage"
              className="h-10 w-32 border border-[#dfe2d9] bg-white px-2 text-sm" />
            <input type="number" min="0" max="100" step="1" value={catalogFilterInputs.maxDiscount}
              onChange={(event) => setCatalogFilterInputs((current) => ({ ...current, maxDiscount: event.target.value }))}
              placeholder="Max discount %" aria-label="Maximum discount percentage"
              className="h-10 w-32 border border-[#dfe2d9] bg-white px-2 text-sm" />
            <input type="number" min="0" step="1" value={catalogFilterInputs.colorCount}
              onChange={(event) => setCatalogFilterInputs((current) => ({ ...current, colorCount: event.target.value }))}
              placeholder="Exact color count" aria-label="Filter by exact number of color variants"
              title="Matches products with exactly this many color variants"
              className="h-10 w-28 border border-[#dfe2d9] bg-white px-2 text-sm" />
            <label className="flex h-10 items-center gap-2 border border-[#dfe2d9] px-2 text-xs text-[#52644f]">
              <input type="checkbox" checked={catalogFilterInputs.stockFilterEnabled}
                onChange={(event) => setCatalogFilterInputs((current) => ({ ...current, stockFilterEnabled: event.target.checked }))}
                className="accent-[#314638]" />
              Stock
              <select value={catalogFilterInputs.stockOperator} disabled={!catalogFilterInputs.stockFilterEnabled}
                onChange={(event) => setCatalogFilterInputs((current) => ({ ...current, stockOperator: event.target.value }))}
                aria-label="Stock comparison operator" className="h-7 border border-[#dfe2d9] bg-white px-1 disabled:bg-[#f4f5f0]">
                <option value="eq">=</option><option value="gte">≥</option><option value="lte">≤</option>
                <option value="gt">&gt;</option><option value="lt">&lt;</option><option value="ne">≠</option>
              </select>
              <input type="number" min="0" step="1" value={catalogFilterInputs.stockValue}
                disabled={!catalogFilterInputs.stockFilterEnabled}
                onChange={(event) => setCatalogFilterInputs((current) => ({ ...current, stockValue: event.target.value }))}
                aria-label="Stock comparison value (default 10)" title="Default value: 10 units"
                className="h-7 w-14 border border-[#dfe2d9] px-1 disabled:bg-[#f4f5f0]" />
            </label>
            <button type="submit" className="h-10 bg-[#314638] px-4 text-xs font-medium text-white hover:bg-[#25372b]">Apply filters</button>
            {(catalogQuery || Object.keys(catalogFilters).length > 0) && <button type="button" onClick={clearCatalogSearch} className="h-10 border border-[#dfe2d9] px-3 text-xs text-[#52644f] hover:bg-[#f4f5f0]">Clear filters</button>}
          </form>
          <label className="grid gap-1 text-[10px] text-[#788174]">Sort products by
            <select value={catalogSortBy} onChange={(event) => { setCatalogSortBy(event.target.value); setCatalogPage(0); }}
              className="h-10 border border-[#dfe2d9] bg-white px-2 text-sm">
              <option value="createdAt">Date added</option>
              <option value="name">Name</option>
              <option value="productCode">Product code</option>
              <option value="price">Price</option>
              <option value="offerPrice">Sale price</option>
              <option value="discount">Discount percentage</option>
              <option value="stockCount">Stock</option>
              <option value="listDate">Listed date</option>
            </select>
          </label>
          <select aria-label="Product sort direction" value={catalogDirection}
            onChange={(event) => { setCatalogDirection(event.target.value); setCatalogPage(0); }}
            className="h-10 border border-[#dfe2d9] bg-white px-2 text-sm">
            <option value="asc">Ascending</option>
            <option value="desc">Descending</option>
          </select>
          </div>
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
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="border-b border-[#e4e6de] bg-[#fbfbf8] text-xs text-[#858a80]"><tr><th className="px-4 py-3 font-medium">Product / code</th><th className="px-4 py-3 font-medium">Category</th><th className="px-4 py-3 font-medium">Price</th><th className="px-4 py-3 font-medium">Total stock</th><th className="px-4 py-3 font-medium">Colors</th>{!managerMode && <th className="px-4 py-3 text-right font-medium">Action</th>}</tr></thead>
              <tbody>{products.map((product) => {
                const variants = product.variants || [];
                const expanded = expandedProductId === product.id;
                return (
                  <Fragment key={product.id}>
                    <tr id={`product-row-${product.id}`}
                      className={`border-b border-[#eef0ea] ${product.id === lastUpdatedProduct?.id ? "bg-emerald-50" : ""}`}>
                      <td className="px-4 py-3 font-medium text-[#354237]"><span className="inline-flex items-center gap-2">{product.name}{product.id === lastUpdatedProduct?.id && <span title="Updated successfully" aria-label="Updated successfully" className="h-2 w-2 rounded-full bg-emerald-600" />}</span><span className="mt-1 block font-mono text-[11px] font-normal text-[#858a80]">{product.productCode || "Code pending"}</span></td>
                      <td className="px-4 py-3 text-[#687064]">{product.category}</td>
                      <td className="px-4 py-3 text-[#687064]">${Number(product.offerPrice ?? product.price).toFixed(2)}</td>
                      <td className="px-4 py-3 font-medium text-[#687064]">{product.stockCount ?? "—"}</td>
                      <td className="px-4 py-3 text-[#687064]">
                        {variants.length > 0 ? (
                          <button
                            type="button"
                            onClick={() => setExpandedProductId(expanded ? null : product.id)}
                            aria-expanded={expanded}
                            aria-controls={`product-variants-${product.id}`}
                            className="inline-flex items-center gap-2 rounded border border-[#dfe2d9] bg-white px-2.5 py-1.5 text-xs text-[#52644f] hover:bg-[#f4f5f0]"
                          >
                            {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                            {variants.length} {variants.length === 1 ? "color" : "colors"}
                          </button>
                        ) : <span className="text-xs text-[#858a80]">No variants</span>}
                      </td>
                      {!managerMode && <td className="px-4 py-3 text-right"><button onClick={() => editProduct(product)} aria-label={`Edit ${product.name}`} className="rounded p-2 text-[#71816d] hover:bg-[#eef1e9]"><Pencil size={15} /></button><button onClick={() => deleteProduct(product)} aria-label={`Delete ${product.name}`} className="rounded p-2 text-[#92978e] hover:bg-red-50 hover:text-red-700"><Trash2 size={15} /></button></td>}
                    </tr>
                    {expanded && <tr id={`product-variants-${product.id}`} className="border-b border-[#eef0ea] bg-[#fbfbf8]">
                      <td colSpan={managerMode ? 5 : 6} className="px-4 py-3">
                        <div className="overflow-x-auto">
                          <table className="w-full text-left text-xs">
                            <thead className="text-[#858a80]">
                              <tr><th className="py-2 font-medium">Color</th><th className="py-2 font-medium">Variant ID</th><th className="py-2 text-right font-medium">Stock</th><th className="py-2 text-right font-medium">Availability</th></tr>
                            </thead>
                            <tbody>{variants.map((variant, index) => {
                              const stock = Math.max(0, Number(variant.stockCount) || 0);
                              return <tr key={variant.id || variant.variantId || index} className="border-t border-[#e4e6de] text-[#52644f]">
                                <td className="py-2 font-medium">{variant.color || `Variant ${index + 1}`}</td>
                                <td className="py-2 font-mono text-[10px] text-[#858a80]">{variant.id || variant.variantId || "—"}</td>
                                <td className="py-2 text-right font-medium">{stock}</td>
                                <td className="py-2 text-right">{stock > 0 ? "In stock" : "Out of stock"}</td>
                              </tr>;
                            })}</tbody>
                          </table>
                          {!managerMode && <p className="mt-2 text-[11px] text-[#858a80]">Use Edit to update stock for each color.</p>}
                        </div>
                      </td>
                    </tr>}
                  </Fragment>
                );
              })}</tbody>
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
