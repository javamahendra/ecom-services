import { ArrowRight, Heart, Trash2 } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useSelector } from "react-redux";
import { toast } from "react-toastify";
import { request } from "../../api";
import LoadingState from "../../components/LoadingState";
import { productImage } from "../../components/ProductCard";

export default function WishlistPage() {
  const token = useSelector((state) => state.auth?.token);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  const loadWishlist = useCallback(async () => {
    try {
      const result = await request("/api/wishlist", { token });
      setItems(result?.items || []);
    } catch (error) {
      toast.error(error.message || "Could not load your wishlist.");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    setLoading(true);
    loadWishlist();
  }, [loadWishlist]);

  const removeItem = async (item) => {
    try {
      const result = await request(
        `/api/wishlist/items/${encodeURIComponent(item.productId)}/${encodeURIComponent(item.variantId)}`,
        { method: "DELETE", token },
      );
      setItems(result?.items || items.filter((entry) => (
        entry.productId !== item.productId || entry.variantId !== item.variantId
      )));
      toast.success("Removed from your wishlist.");
    } catch (error) {
      toast.error(error.message || "Could not remove this item.");
    }
  };

  if (loading) return <LoadingState label="Loading your wishlist…" />;

  return (
    <div>
      <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-[#788174]">Saved for later</p>
      <h1 className="mt-2 text-3xl font-normal tracking-[-0.04em] text-[#29382d]">My wishlist</h1>
      {items.length === 0 ? (
        <div className="mt-6 border border-[#e4e6de] bg-white px-5 py-12 text-center">
          <Heart size={24} className="mx-auto text-[#71816d]" />
          <p className="mt-4 text-sm text-[#687064]">Your wishlist is empty.</p>
          <Link to="/" className="mt-4 inline-flex items-center gap-2 text-xs text-[#52644f] hover:underline">
            Browse products <ArrowRight size={14} />
          </Link>
        </div>
      ) : (
        <div className="mt-6 divide-y divide-[#eef0ea] border border-[#e4e6de] bg-white">
          {items.map((item) => (
            <article key={`${item.productId}-${item.variantId}`} className="flex flex-wrap items-center gap-4 p-4 sm:gap-6">
              <Link to={`/products/${encodeURIComponent(item.productId)}`} className="h-24 w-20 shrink-0 overflow-hidden bg-[#eeefe9] sm:h-28 sm:w-24">
                <img
                  src={item.imageUrls?.[0] || productImage({})}
                  alt={item.productName}
                  className="h-full w-full object-cover"
                />
              </Link>
              <div className="min-w-0 flex-1">
                <Link to={`/products/${encodeURIComponent(item.productId)}`} className="text-sm font-medium text-[#354237] hover:text-[#52644f] hover:underline">
                  {item.productName}
                </Link>
                <p className="mt-1 text-xs text-[#858a80]">{item.category || item.productType}{item.color ? ` · ${item.color}` : ""}</p>
                <p className="mt-2 text-sm text-[#454d43]">${Number(item.unitPrice || 0).toFixed(2)}</p>
                {!item.inStock && <p className="mt-1 text-xs text-[#9a6257]">Currently out of stock</p>}
              </div>
              <button
                type="button"
                onClick={() => removeItem(item)}
                aria-label={`Remove ${item.productName} from wishlist`}
                className="inline-flex items-center gap-2 border border-[#dfe2d9] px-3 py-2 text-xs text-[#687064] transition hover:border-[#9a6257] hover:text-[#8b4d43]"
              >
                <Trash2 size={14} /> <span className="hidden sm:inline">Remove</span>
              </button>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
