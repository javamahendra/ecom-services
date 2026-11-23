import { ArrowLeft, Minus, Plus } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
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
  const [loading, setLoading] = useState(true);
  const dispatch = useDispatch();
  const cart = useSelector((state) => state.cart);

  const variantId = product?.variantId || product?.variants?.[0]?.id || "default";
  const cartItemId = product ? `${product.id}-${variantId}` : null;
  const quantity = cart.find((item) => item.id === cartItemId)?.quantity || 0;

  useEffect(() => {
    let active = true;
    setLoading(true);
    setProduct(null);
    getProduct(id)
      .then((data) => { if (active) setProduct(normalizeProduct(data)); })
      .catch((error) => {
        if (!active) return;
        const fallbackProduct = fallbackProducts.find((item) => item.id === id);
        setProduct(fallbackProduct || null);
        if (!fallbackProduct) toast.error(error.message || "Could not load this product.");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id]);

  if (loading) return <main className="mx-auto max-w-[1240px] px-5 sm:px-8 lg:px-12"><LoadingState label="Loading product details…" /></main>;

  if (!product) {
    return <main className="mx-auto max-w-3xl px-6 py-32 text-center"><h1 className="text-3xl text-[#29382d]">This piece is not here.</h1><Link to="/" className="mt-6 inline-flex items-center gap-2 text-sm text-[#52644f]"><ArrowLeft size={15} /> Back to the collection</Link></main>;
  }

  const image = productImage(product);
  const price = Number(product.offerPrice ?? product.price ?? 0);
  const updateCartQuantity = (nextQuantity) => {
    dispatch(setItemQuantity({
      id: cartItemId,
      productId: product.id,
      variantId,
      name: product.name,
      image,
      price,
      color: product.color || product.variants?.[0]?.color || "",
      quantity: nextQuantity,
    }));
    if (quantity === 0 && nextQuantity === 1) toast.success(`${product.name} added to your bag`);
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
          {(product.color || product.variants?.[0]?.color) && <p className="mt-7 text-xs text-[#747a70]">Color <span className="ml-2 font-medium text-[#354237]">{product.color || product.variants[0].color}</span></p>}
          <div className="mt-8 flex items-center gap-4">
            <div className="flex h-12 items-center border border-[#dfe2d9]">
              <button onClick={() => updateCartQuantity(quantity - 1)} disabled={quantity === 0} className="grid h-11 w-10 place-items-center text-[#5b6459] disabled:cursor-not-allowed disabled:opacity-40" aria-label="Decrease quantity"><Minus size={14} /></button>
              <span className="w-7 text-center text-sm">{quantity}</span>
              <button onClick={() => updateCartQuantity(quantity + 1)} className="grid h-11 w-10 place-items-center text-[#5b6459]" aria-label="Increase quantity"><Plus size={14} /></button>
            </div>
            <span className="text-xs text-[#81877c]">{quantity > 0 ? `${quantity} in your bag` : "Use + to add to your bag"}</span>
          </div>
          <div className="mt-8 border-t border-[#e3e5dd] pt-5 text-xs leading-6 text-[#81877c]">Thoughtfully selected. Made for everyday living.<br />Free shipping on orders over $100.</div>
        </div>
      </div>
    </main>
  );
}
