import { ArrowLeft, ArrowRight, Minus, Plus, ShoppingBag } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { toast } from "react-toastify";
import { normalizeProduct } from "../api";
import { getProduct } from "../services/productService";
import { fallbackProducts } from "../data/products";
import { productImage } from "../components/ProductCard";
import { setItemQuantity } from "../store/slices/cartSlice";
import LoadingState from "../components/LoadingState";

export default function ProductDetails() {
  const { id } = useParams();
  const [product, setProduct] = useState(null);
  const [selectedVariantId, setSelectedVariantId] = useState("");
  const [loading, setLoading] = useState(true);
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const cart = useSelector((state) => state.cart);

  const variants = product
    ? (product.variants?.length
      ? product.variants
      : [{
        id: product.variantId || "default",
        color: product.color || product.baseColor || "Default",
        imageUrls: product.image ? [product.image] : [],
        stockCount: product.stockCount || 0,
      }])
    : [];
  const selectedVariant = variants.find((variant) => (variant.id || variant.variantId) === selectedVariantId) || variants[0];
  const variantId = selectedVariant?.id || selectedVariant?.variantId || "default";
  const variantStockCount = Math.max(0, Number(selectedVariant?.stockCount) || 0);
  const totalStockCount = variants.reduce((total, variant) => total + Math.max(0, Number(variant.stockCount) || 0), 0);
  const cartItemId = product ? `${product.id}-${variantId}` : null;
  const quantity = cart.find((item) => item.id === cartItemId)?.quantity || 0;

  useEffect(() => {
    let active = true;
    setLoading(true);
    setProduct(null);
    getProduct(id)
      .then((data) => {
        if (!active) return;
        const normalized = normalizeProduct(data);
        setProduct(normalized);
        setSelectedVariantId(normalized.variants?.[0]?.id || normalized.variantId || "default");
      })
      .catch((error) => {
        if (!active) return;
        const fallbackProduct = fallbackProducts.find((item) => item.id === id);
        setProduct(fallbackProduct || null);
        setSelectedVariantId(fallbackProduct?.variants?.[0]?.id || fallbackProduct?.variantId || "default");
        if (!fallbackProduct) toast.error(error.message || "Could not load this product.");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id]);

  if (loading) return <main className="mx-auto max-w-[1240px] px-5 sm:px-8 lg:px-12"><LoadingState label="Loading product details…" /></main>;

  if (!product) {
    return <main className="mx-auto max-w-3xl px-6 py-32 text-center"><h1 className="text-3xl text-[#29382d]">This piece is not here.</h1><Link to="/" className="mt-6 inline-flex items-center gap-2 text-sm text-[#52644f]"><ArrowLeft size={15} /> Back to the collection</Link></main>;
  }

  const image = selectedVariant?.imageUrls?.[0] || productImage(product);
  const price = Number(product.offerPrice ?? product.price ?? 0);
  const updateCartQuantity = (nextQuantity) => {
    if (nextQuantity > variantStockCount) {
      toast.info(variantStockCount === 0 ? "This color is out of stock." : `Only ${variantStockCount} available in this color.`);
      return false;
    }
    dispatch(setItemQuantity({
      id: cartItemId,
      productId: product.id,
      variantId,
      name: product.name,
      image,
      price,
      color: selectedVariant?.color || product.color || "",
      quantity: nextQuantity,
    }));
    if (quantity === 0 && nextQuantity === 1) toast.success(`${product.name} added to your bag`);
    return true;
  };
  const addToBag = () => {
    if (quantity > 0) {
      toast.info(`${product.name} is already in your bag`);
      return;
    }
    updateCartQuantity(1);
  };
  const buyNow = () => {
    if (updateCartQuantity(quantity || 1)) navigate("/checkout");
  };

  return (
    <main className="mx-auto max-w-[1240px] px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <Link to="/" className="inline-flex items-center gap-2 text-xs text-[#777e73] transition hover:text-[#314638]"><ArrowLeft size={14} /> Back to collection</Link>
      <div className="mt-7 grid gap-10 md:grid-cols-2 md:gap-16">
        <div className="overflow-hidden bg-[#eeefe9]"><img src={image} alt={product.name} className="aspect-[4/4.6] h-full w-full object-cover" /></div>
        <div className="flex flex-col justify-center py-4">
          <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-[#788174]">{product.category || product.productType || "Form & Field"}</p>
          <h1 className="mt-4 text-4xl font-normal leading-tight tracking-[-0.05em] text-[#29382d] sm:text-5xl">{product.name}</h1>
          <p className="mt-5 text-lg text-[#455345]">${price.toFixed(2)}</p>
          <div className="my-7 h-px bg-[#e3e5dd]" />
          <p className="max-w-lg text-sm leading-7 text-[#73796f]">{product.description || "A considered everyday essential, chosen for its thoughtful materials, lasting quality, and simple usefulness."}</p>
          <div className="mt-7">
            <label htmlFor="product-color" className="mb-2 block text-xs text-[#747a70]">Color</label>
            <select
              id="product-color"
              value={variantId}
              onChange={(event) => setSelectedVariantId(event.target.value)}
              className="w-full max-w-sm border border-[#dfe2d9] bg-white px-3 py-3 text-sm text-[#354237] focus:border-[#52644f] focus:outline-none"
            >
              {variants.map((variant, index) => {
                const color = variant.color || `Variant ${index + 1}`;
                const stockCount = Math.max(0, Number(variant.stockCount) || 0);
                return (
                  <option key={variant.id || variant.variantId || color} value={variant.id || variant.variantId || "default"}>
                    {color} — {stockCount > 0 ? `${stockCount} in stock` : "out of stock"}
                  </option>
                );
              })}
            </select>
            <p className="mt-2 text-xs text-[#81877c]">
              {variantStockCount > 0 ? `${variantStockCount} available in ${selectedVariant?.color || "this color"}` : "This color is out of stock"}
              {variants.length > 1 && ` · ${totalStockCount} total across all colors`}
            </p>
          </div>
          {variants.length > 1 && (
            <details className="mt-5 max-w-sm border-y border-[#e3e5dd]">
              <summary className="cursor-pointer py-3 text-xs font-medium text-[#52644f]">
                View all colors and stock ({variants.length})
              </summary>
              <div className="overflow-x-auto pb-3">
                <table className="w-full text-left text-xs">
                  <thead className="text-[#81877c]">
                    <tr>
                      <th className="py-2 font-medium">Color</th>
                      <th className="py-2 text-right font-medium">Stock</th>
                      <th className="py-2 pl-4 text-right font-medium">Availability</th>
                    </tr>
                  </thead>
                  <tbody>
                    {variants.map((variant, index) => {
                      const color = variant.color || `Variant ${index + 1}`;
                      const stockCount = Math.max(0, Number(variant.stockCount) || 0);
                      const id = variant.id || variant.variantId || "default";
                      return (
                        <tr key={id} className="border-t border-[#eef0ea]">
                          <td className="py-2">
                            <button type="button" onClick={() => setSelectedVariantId(id)} className="text-[#354237] underline-offset-2 hover:underline">
                              {color}{id === variantId ? " (selected)" : ""}
                            </button>
                          </td>
                          <td className="py-2 text-right text-[#354237]">{stockCount}</td>
                          <td className="py-2 pl-4 text-right text-[#81877c]">{stockCount > 0 ? "In stock" : "Out of stock"}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </details>
          )}
          <div className="mt-8 flex items-center gap-4">
            <div className="flex h-12 items-center border border-[#dfe2d9]">
              <button onClick={() => updateCartQuantity(quantity - 1)} disabled={quantity === 0} className="grid h-11 w-10 place-items-center text-[#5b6459] disabled:cursor-not-allowed disabled:opacity-40" aria-label="Decrease quantity"><Minus size={14} /></button>
              <span className="w-7 text-center text-sm">{quantity}</span>
              <button onClick={() => updateCartQuantity(quantity + 1)} disabled={variantStockCount === 0 || quantity >= variantStockCount} className="grid h-11 w-10 place-items-center text-[#5b6459] disabled:cursor-not-allowed disabled:opacity-40" aria-label="Increase quantity"><Plus size={14} /></button>
            </div>
            <span className="text-xs text-[#81877c]">
              {variantStockCount === 0 ? "Out of stock in this color" : quantity > 0 ? `${quantity} in your bag` : "Use + to add to your bag"}
            </span>
          </div>
          <div className="mt-6 flex max-w-sm flex-col gap-3 sm:flex-row">
            <button
              type="button"
              onClick={addToBag}
              disabled={variantStockCount === 0}
              className="flex h-12 flex-1 items-center justify-center gap-2 border border-[#314638] text-xs font-medium uppercase tracking-[0.12em] text-[#314638] transition hover:bg-[#eef0ea] disabled:cursor-not-allowed disabled:opacity-50"
            >
              <ShoppingBag size={15} /> Add to bag
            </button>
            <button
              type="button"
              onClick={buyNow}
              disabled={variantStockCount === 0}
              className="flex h-12 flex-1 items-center justify-center gap-2 bg-[#314638] text-xs font-medium uppercase tracking-[0.12em] text-white transition hover:bg-[#25372b] disabled:cursor-not-allowed disabled:opacity-50"
            >
              Buy now <ArrowRight size={14} />
            </button>
          </div>
          <div className="mt-8 border-t border-[#e3e5dd] pt-5 text-xs leading-6 text-[#81877c]">Thoughtfully selected. Made for everyday living.<br />Free shipping on orders over $100.</div>
        </div>
      </div>
    </main>
  );
}
