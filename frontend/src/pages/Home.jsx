import { ArrowDown, ArrowLeft, ArrowRight, Leaf, PackageCheck, Search, Sparkles } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useSelector } from "react-redux";
import { toast } from "react-toastify";
import { request, normalizeProduct } from "../api";
import { getBanner } from "../services/bannerService";
import { getProducts } from "../services/productService";
import { fallbackProducts } from "../data/products";
import { ProductCard } from "../components/ProductCard";

const PAGE_SIZE = 8;
const MIN_LOADING_MS = 1200;
const CATALOG_CATEGORIES = [
  "Accessories",
  "Beauty",
  "Clothing",
  "Electronics",
  "Home",
  "Kitchen",
  "Shoes",
  "Sports",
  "Stationery",
  "Toys",
];
const DEFAULT_BANNER = {
  enabled: true,
  eyebrow: "A more thoughtful everyday",
  heading: "Good things,",
  accentText: "well chosen.",
  description: "Useful, beautiful essentials made with intention. A little less, a lot better.",
  imageUrl: "https://images.unsplash.com/photo-1490312278390-ab64016e0aa9?auto=format&fit=crop&w=1500&q=90",
  imageAlt: "A calm, light-filled home with considered everyday objects",
  buttonText: "Explore the collection",
};

function ProductGridSkeleton() {
  return (
    <div role="status" aria-label="Loading products" aria-busy="true" className="relative mt-5 flex min-h-[360px] flex-col items-center justify-center overflow-hidden border border-[#e4e6de] bg-white px-6 py-12">
      <span className="sr-only">Loading products</span>
      <div aria-hidden="true" className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-[#eef1e9] via-white to-white" />
      <div className="relative grid h-20 w-20 place-items-center">
        <span className="absolute inset-0 rounded-full border-[3px] border-[#e2e7de]" />
        <span className="absolute inset-0 animate-spin rounded-full border-[3px] border-transparent border-t-[#314638]" />
        <span className="grid h-12 w-12 place-items-center rounded-full bg-[#314638] text-xs font-semibold tracking-tight text-white shadow-lg shadow-[#314638]/20">F/F</span>
      </div>
      <p className="relative mt-6 text-sm font-medium tracking-wide text-[#354237]">Finding your next favorite</p>
      <p className="relative mt-2 text-xs text-[#858a80]">Gathering the collection for you…</p>
      <div aria-hidden="true" className="relative mt-6 h-1 w-40 overflow-hidden rounded-full bg-[#e7eae3]">
        <span className="absolute inset-y-0 left-0 w-1/2 animate-[collection-loader_1.2s_ease-in-out_infinite] rounded-full bg-[#52644f]" />
      </div>
    </div>
  );
}

function ProductPagination({ page, totalPages, loading, onPageChange }) {
  if (totalPages <= 1) return null;

  return (
    <nav aria-label="Product pages" className="flex items-center justify-center gap-2">
      <button
        type="button"
        onClick={() => onPageChange(Math.max(0, page - 1))}
        disabled={page === 0 || loading}
        className="inline-flex h-10 items-center gap-2 border border-[#dfe2d9] px-3 text-xs text-[#586257] transition hover:border-[#314638] disabled:cursor-not-allowed disabled:opacity-40"
      >
        <ArrowLeft size={14} /><span className="hidden sm:inline">Previous</span>
      </button>
      {Array.from({ length: totalPages }, (_, index) => (
        <button
          type="button"
          key={index}
          onClick={() => onPageChange(index)}
          disabled={loading}
          aria-current={index === page ? "page" : undefined}
          aria-label={`Page ${index + 1}`}
          className={`grid h-10 min-w-10 place-items-center border px-3 text-xs transition ${index === page ? "border-[#314638] bg-[#314638] text-white" : "border-[#dfe2d9] text-[#586257] hover:border-[#314638]"} disabled:cursor-wait`}
        >
          {index + 1}
        </button>
      ))}
      <button
        type="button"
        onClick={() => onPageChange(Math.min(totalPages - 1, page + 1))}
        disabled={page >= totalPages - 1 || loading}
        className="inline-flex h-10 items-center gap-2 border border-[#dfe2d9] px-3 text-xs text-[#586257] transition hover:border-[#314638] disabled:cursor-not-allowed disabled:opacity-40"
      >
        <span className="hidden sm:inline">Next</span><ArrowRight size={14} />
      </button>
    </nav>
  );
}

export default function Home() {
  const user = useSelector((state) => state.auth);
  const navigate = useNavigate();
  const location = useLocation();
  const [products, setProducts] = useState([]);
  const [category, setCategory] = useState("All");
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [usingFallback, setUsingFallback] = useState(false);
  const [banner, setBanner] = useState(DEFAULT_BANNER);
  const [wishlistIds, setWishlistIds] = useState(() => new Set());
  const categories = useMemo(
    () => ["All", ...new Set([
      ...CATALOG_CATEGORIES,
      ...fallbackProducts.map((product) => product.category),
      ...products.map((product) => product.category),
    ].filter(Boolean))],
    [products],
  );

  useEffect(() => {
    let active = true;
    let loadingTimer;
    const loadingStartedAt = Date.now();
    const params = new URLSearchParams({
      page: String(page),
      size: String(PAGE_SIZE),
      sortBy: "createdAt",
      direction: "desc",
    });
    if (category !== "All") params.set("category", category);
    if (search) params.set("search", search);

    setLoading(true);
    getProducts(params)
      .then((result) => {
        const rows = Array.isArray(result) ? result : result.content || result.items || [];
        if (!active) return;
        setUsingFallback(false);
        setProducts(rows.map(normalizeProduct));
        setTotalPages(Array.isArray(result) ? 1 : result.totalPages || 1);
        setTotalElements(Array.isArray(result) ? rows.length : result.totalElements ?? rows.length);
      })
      .catch(() => {
        if (!active) return;
        setUsingFallback(true);
        const searchTerm = search.toLocaleLowerCase();
        const matchingProducts = fallbackProducts.filter((product) => {
          const matchesCategory = category === "All" || product.category === category;
          const searchableText = [
            product.name,
            product.description,
            product.category,
            product.productType,
            ...(product.variants || []).map((variant) => variant.color),
          ].join(" ").toLocaleLowerCase();
          return matchesCategory && (!searchTerm || searchableText.includes(searchTerm));
        });
        const start = page * PAGE_SIZE;
        setProducts(matchingProducts.slice(start, start + PAGE_SIZE));
        setTotalElements(matchingProducts.length);
        setTotalPages(Math.max(1, Math.ceil(matchingProducts.length / PAGE_SIZE)));
        toast.info("Showing our curated collection while the catalog reconnects.", { toastId: "catalog-offline" });
      })
      .finally(() => {
        const remainingLoadingTime = Math.max(0, MIN_LOADING_MS - (Date.now() - loadingStartedAt));
        if (active) {
          loadingTimer = window.setTimeout(() => setLoading(false), remainingLoadingTime);
        }
      });
    return () => {
      active = false;
      window.clearTimeout(loadingTimer);
    };
  }, [page, category, search]);

  useEffect(() => {
    let active = true;
    getBanner()
      .then((result) => {
        if (active && result) setBanner(result);
      })
      .catch(() => {
        if (active) setBanner(DEFAULT_BANNER);
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    if (!user?.token) {
      setWishlistIds(new Set());
      return () => { active = false; };
    }

    request("/api/wishlist", { token: user.token })
      .then((result) => {
        if (active) setWishlistIds(new Set((result?.items || []).map((item) => `${item.productId}-${item.variantId}`)));
      })
      .catch((error) => {
        if (active) toast.error(error.message || "Could not load your wishlist.");
      });
    return () => { active = false; };
  }, [user?.token]);

  const toggleWishlist = async (product) => {
    if (!user?.token) {
      toast.info("Sign in to save items to your wishlist.");
      navigate("/login", { state: { from: `${location.pathname}${location.search}${location.hash}` } });
      return;
    }

    const variantId = product.variantId || product.variants?.[0]?.id || "default";
    const key = `${product.id}-${variantId}`;
    const isWishlisted = wishlistIds.has(key);
    try {
      const result = await request(
        isWishlisted
          ? `/api/wishlist/items/${encodeURIComponent(product.id)}/${encodeURIComponent(variantId)}`
          : "/api/wishlist/items",
        {
          method: isWishlisted ? "DELETE" : "POST",
          token: user.token,
          ...(!isWishlisted ? { body: { productId: product.id, variantId } } : {}),
        },
      );
      setWishlistIds(new Set((result?.items || []).map((item) => `${item.productId}-${item.variantId}`)));
      toast.success(isWishlisted ? "Removed from your wishlist." : "Added to your wishlist.");
    } catch (error) {
      toast.error(error.message || "Could not update your wishlist.");
    }
  };

  const selectCategory = (selectedCategory) => {
    setCategory(selectedCategory);
    setPage(0);
  };

  const submitSearch = (event) => {
    event.preventDefault();
    setSearch(searchInput.trim());
    setPage(0);
  };

  const clearSearch = () => {
    setSearchInput("");
    setSearch("");
    setPage(0);
  };

  return (
    <>
      <main>
        {banner?.enabled && (
          <section className="relative mx-auto max-w-[1440px] overflow-hidden px-5 pb-12 pt-9 sm:px-8 lg:px-12 lg:pt-12">
            <div className="relative grid min-h-[550px] overflow-hidden bg-[#e8e9df] md:grid-cols-[0.85fr_1.15fr] lg:min-h-[610px]">
              <div className="relative z-10 flex flex-col items-start justify-center px-7 py-14 sm:px-12 lg:px-[13%]">
                <p className="mb-6 flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[0.2em] text-[#586557]"><span className="h-px w-7 bg-[#778273]" /> {banner.eyebrow}</p>
                <h1 className="max-w-[490px] text-[clamp(3.2rem,7vw,6.35rem)] font-normal leading-[0.96] tracking-[-0.065em] text-[#29382d]">{banner.heading}<br /><em className="font-serif font-light">{banner.accentText}</em></h1>
                <p className="mt-7 max-w-[330px] text-sm leading-7 text-[#687064]">{banner.description}</p>
                <a href="#collection" className="mt-8 inline-flex items-center gap-3 bg-[#314638] px-6 py-3.5 text-xs font-medium uppercase tracking-[0.12em] text-white transition hover:bg-[#203327]">{banner.buttonText} <ArrowRight size={15} /></a>
                <a href="#collection" className="mt-12 inline-flex items-center gap-2 text-[10px] uppercase tracking-[0.16em] text-[#82877d] transition hover:text-[#314638]"><ArrowDown size={13} /> Find your everyday</a>
              </div>
              <div className="relative min-h-[320px] md:min-h-full">
                <img src={banner.imageUrl} alt={banner.imageAlt} className="absolute inset-0 h-full w-full object-cover" />
                <div className="absolute inset-0 bg-gradient-to-r from-[#e8e9df] via-transparent to-transparent md:bg-gradient-to-r md:from-[#e8e9df]/20 md:via-transparent" />
                <div className="absolute bottom-5 right-5 bg-[#f7f7f3]/90 px-4 py-3 text-[10px] uppercase tracking-[0.14em] text-[#586557] backdrop-blur sm:bottom-8 sm:right-8">Objects for the everyday</div>
              </div>
            </div>
          </section>
        )}

        <section id="collection" className="mx-auto max-w-[1440px] scroll-mt-24 px-5 pt-20 sm:px-8 lg:px-12 lg:pt-28">
          <div className="flex flex-col justify-between gap-7 md:flex-row md:items-end">
            <div>
              <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-[#788174]">The collection</p>
              <h2 className="mt-3 text-4xl font-normal tracking-[-0.045em] text-[#29382d] sm:text-5xl">Made to live with.</h2>
            </div>
          </div>
          <div className="mt-6 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <form onSubmit={submitSearch} role="search" className="flex w-full max-w-md gap-2">
              <input
                type="search"
                value={searchInput}
                onChange={(event) => setSearchInput(event.target.value)}
                placeholder="Search products..."
                aria-label="Search products"
                className="h-10 min-w-0 flex-1 border border-[#dfe2d9] bg-white px-3 text-sm outline-none focus:border-[#71816d]"
              />
              <button type="submit" aria-label="Search products" className="grid h-10 w-10 shrink-0 place-items-center bg-[#314638] text-white transition hover:bg-[#25372b]">
                <Search size={16} />
              </button>
              {search && <button type="button" onClick={clearSearch} className="h-10 border border-[#dfe2d9] px-3 text-xs text-[#52644f] hover:bg-white">Clear</button>}
            </form>
            <div className="flex flex-wrap gap-2">
              {categories.map((item) => <button key={item} onClick={() => selectCategory(item)} className={`rounded-full border px-4 py-2 text-xs transition ${category === item ? "border-[#314638] bg-[#314638] text-white" : "border-[#dfe2d9] text-[#667064] hover:border-[#9da69a]"}`}>{item}</button>)}
            </div>
          </div>
          <div aria-live="polite" className="mt-4 flex min-h-5 items-center gap-2 text-xs text-[#858a80]">
            {loading
              ? "Loading products…"
              : `${totalElements} ${totalElements === 1 ? "piece" : "pieces"}${usingFallback ? " · curated collection" : ""}`}
          </div>
          <div className="mt-5">
            <ProductPagination page={page} totalPages={totalPages} loading={loading} onPageChange={setPage} />
          </div>
          {loading ? <ProductGridSkeleton /> : (
            <div className="mt-5 grid grid-cols-2 gap-x-4 gap-y-9 sm:gap-x-6 sm:gap-y-12 lg:grid-cols-4">
              {products.map((product, index) => {
                const variantId = product.variantId || product.variants?.[0]?.id || "default";
                return (
                  <ProductCard
                    key={product.id}
                    product={product}
                    index={index}
                    isWishlisted={wishlistIds.has(`${product.id}-${variantId}`)}
                    onToggleWishlist={toggleWishlist}
                  />
                );
              })}
            </div>
          )}
          {!loading && products.length === 0 && <p className="py-16 text-center text-sm text-[#858a80]">Nothing in this collection just yet.</p>}
          <div className="mt-12">
            <ProductPagination page={page} totalPages={totalPages} loading={loading} onPageChange={setPage} />
          </div>
        </section>

        <section className="mx-auto mt-24 grid max-w-[1440px] gap-9 border-y border-[#e5e7df] px-6 py-10 sm:grid-cols-3 sm:px-10 lg:px-16">
          {[{ Icon: Leaf, title: "Considered materials", text: "Thoughtful choices, made to last." }, { Icon: Sparkles, title: "Useful by design", text: "Every detail earns its place." }, { Icon: PackageCheck, title: "Packed with care", text: "A little joy, right to your door." }].map(({ Icon, title, text }) => (
            <div key={title} className="flex items-start gap-4"><span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-[#e9ece5] text-[#52644f]"><Icon size={18} strokeWidth={1.5} /></span><div><h3 className="text-sm font-medium text-[#354237]">{title}</h3><p className="mt-1.5 text-xs text-[#858a80]">{text}</p></div></div>
          ))}
        </section>
      </main>
    </>
  );
}
