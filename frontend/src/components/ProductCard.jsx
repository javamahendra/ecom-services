import { ArrowUpRight, Heart, Plus } from "lucide-react";
import { Link } from "react-router-dom";
import { useDispatch } from "react-redux";
import { toast } from "react-toastify";
import { useEffect, useState } from "react";
import { addItem } from "../store/slices/cartSlice";

export function productImage(product) {
  return product.image || product.variants?.[0]?.imageUrls?.[0] || "https://images.unsplash.com/photo-1490312278390-ab64016e0aa9?auto=format&fit=crop&w=900&q=85";
}

export function ProductCard({ product, index = 0, isWishlisted = false, onToggleWishlist }) {
  const dispatch = useDispatch();
  const image = productImage(product);
  const [imageLoaded, setImageLoaded] = useState(false);
  const [imageError, setImageError] = useState(false);
  const price = Number(product.offerPrice ?? product.price ?? 0);

  useEffect(() => {
    setImageLoaded(false);
    setImageError(false);
  }, [image]);

  const add = (event) => {
    event.preventDefault();
    event.stopPropagation();
    dispatch(addItem({
      id: `${product.id}-${product.variantId || product.variants?.[0]?.id || "default"}`,
      productId: product.id,
      variantId: product.variantId || product.variants?.[0]?.id || "default",
      name: product.name,
      image,
      price,
      color: product.color || product.variants?.[0]?.color || "",
    }));
    toast.success(`${product.name} added to your bag`);
  };

  return (
    <article className="group">
      <Link to={`/products/${product.id}`} className="relative block overflow-hidden rounded-[3px] bg-[#eeefe9]">
        <div className="aspect-[4/4.7] overflow-hidden">
          {!imageLoaded && (
            <div aria-hidden="true" className="absolute inset-0 z-[1] animate-pulse bg-gradient-to-br from-[#e7e9e2] via-[#f7f7f3] to-[#e1e5dc]" />
          )}
          {imageError ? (
            <div className="grid h-full w-full place-items-center text-xs text-[#858a80]">Image unavailable</div>
          ) : (
            <img
              src={image}
              alt={product.name}
              loading={index > 3 ? "lazy" : "eager"}
              onLoad={() => setImageLoaded(true)}
              onError={() => {
                setImageError(true);
                setImageLoaded(true);
              }}
              className={`h-full w-full object-cover transition duration-700 ease-out group-hover:scale-[1.04] ${imageLoaded ? "opacity-100" : "opacity-0"}`}
            />
          )}
        </div>
        {product.offerPrice && <span className="absolute left-3 top-3 bg-[#f7f7f3] px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.12em] text-[#394b3c]">Considered price</span>}
        <button
          type="button"
          onClick={(event) => {
            event.preventDefault();
            event.stopPropagation();
            onToggleWishlist?.(product);
          }}
          aria-label={isWishlisted ? `Remove ${product.name} from wishlist` : `Add ${product.name} to wishlist`}
          aria-pressed={isWishlisted}
          className={`absolute right-3 top-3 z-[2] grid h-10 w-10 place-items-center rounded-full shadow-sm transition ${isWishlisted ? "bg-[#314638] text-white" : "bg-white/95 text-[#52644f] hover:bg-[#314638] hover:text-white"}`}
        >
          <Heart size={17} fill={isWishlisted ? "currentColor" : "none"} />
        </button>
        <span className="absolute bottom-3 right-3 grid h-10 w-10 translate-y-2 place-items-center rounded-full bg-white text-[#344b3b] opacity-0 shadow-sm transition duration-300 hover:bg-[#314638] hover:text-white group-hover:translate-y-0 group-hover:opacity-100"><ArrowUpRight size={17} /></span>
      </Link>
      <div className="flex items-start justify-between gap-2 pt-4">
        <Link to={`/products/${product.id}`} className="min-w-0">
          <p className="truncate text-[15px] font-medium text-[#2c332c]">{product.name}</p>
          <p className="mt-1 text-xs text-[#898e84]">{product.category || product.productType || "Everyday essential"}</p>
        </Link>
        <button onClick={add} aria-label={`Add ${product.name} to bag`} className="mt-0.5 grid h-8 w-8 shrink-0 place-items-center rounded-full border border-[#dfe2d9] text-[#586257] transition hover:border-[#314638] hover:bg-[#314638] hover:text-white"><Plus size={16} /></button>
      </div>
      <p className="mt-2 text-sm text-[#454d43]">${price.toFixed(2)}</p>
    </article>
  );
}
