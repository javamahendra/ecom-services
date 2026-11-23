import { ArrowLeft, ArrowRight, Minus, Plus, ShoppingBag, Trash2 } from "lucide-react";
import { Link } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { decrementItem, incrementItem, removeItem } from "../store/slices/cartSlice";
import { productImage } from "../components/ProductCard";

export default function Cart() {
  const items = useSelector((state) => state.cart);
  const dispatch = useDispatch();
  const subtotal = items.reduce((sum, item) => sum + item.price * item.quantity, 0);

  return (
    <main className="mx-auto min-h-[55vh] max-w-[1180px] px-5 py-10 sm:px-8 lg:px-12 lg:py-16">
      <div className="flex items-end justify-between border-b border-[#e1e4dc] pb-6">
        <div><p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-[#788174]">Your selection</p><h1 className="mt-2 text-4xl font-normal tracking-[-0.05em] text-[#29382d]">Your bag <span className="text-2xl text-[#969b91]">({items.reduce((sum, item) => sum + item.quantity, 0)})</span></h1></div>
        <Link to="/" className="hidden items-center gap-2 text-xs text-[#747a70] hover:text-[#314638] sm:flex"><ArrowLeft size={14} /> Continue shopping</Link>
      </div>
      {items.length === 0 ? (
        <div className="flex flex-col items-center py-24 text-center">
          <span className="grid h-16 w-16 place-items-center rounded-full bg-[#e9ece5] text-[#586957]"><ShoppingBag size={24} strokeWidth={1.4} /></span>
          <h2 className="mt-6 text-2xl font-normal text-[#354237]">Your bag is taking a breather.</h2>
          <p className="mt-2 text-sm text-[#858a80]">Find something you’ll reach for every day.</p>
          <Link to="/" className="mt-7 inline-flex items-center gap-2 bg-[#314638] px-6 py-3.5 text-xs uppercase tracking-[0.12em] text-white hover:bg-[#25372b]">Explore the collection <ArrowRight size={14} /></Link>
        </div>
      ) : (
        <div className="grid gap-12 pt-8 lg:grid-cols-[1fr_340px]">
          <div>
            {items.map((item) => <article key={item.id} className="flex gap-4 border-b border-[#e6e8e1] py-5 first:pt-0 sm:gap-6">
              <Link to={`/products/${item.productId}`} className="h-28 w-24 shrink-0 overflow-hidden bg-[#eeefe9] sm:h-36 sm:w-32"><img src={item.image || productImage(item)} alt={item.name} className="h-full w-full object-cover" /></Link>
              <div className="flex min-w-0 flex-1 flex-col justify-between py-1 sm:flex-row sm:items-start">
                <div><Link to={`/products/${item.productId}`} className="text-sm font-medium text-[#354237] hover:text-[#687b60]">{item.name}</Link>{item.color && <p className="mt-1 text-xs text-[#858a80]">{item.color}</p>}<p className="mt-2 text-sm text-[#535d51]">${item.price.toFixed(2)}</p></div>
                <div className="mt-4 flex items-center justify-between gap-5 sm:mt-0 sm:flex-col sm:items-end">
                  <div className="flex h-8 items-center border border-[#dfe2d9]">
                    <button onClick={() => item.quantity === 1 ? dispatch(removeItem(item.id)) : dispatch(decrementItem(item.id))} className="grid h-7 w-8 place-items-center text-[#677064]" aria-label="Decrease quantity"><Minus size={12} /></button>
                    <span className="w-6 text-center text-xs">{item.quantity}</span>
                    <button onClick={() => dispatch(incrementItem(item.id))} className="grid h-7 w-8 place-items-center text-[#677064]" aria-label="Increase quantity"><Plus size={12} /></button>
                  </div>
                  <button onClick={() => dispatch(removeItem(item.id))} className="inline-flex items-center gap-1 text-xs text-[#92978e] hover:text-[#8b4d43]"><Trash2 size={13} /><span className="sm:hidden">Remove</span></button>
                </div>
              </div>
            </article>)}
            <Link to="/" className="mt-5 inline-flex items-center gap-2 text-xs text-[#747a70] hover:text-[#314638] sm:hidden"><ArrowLeft size={14} /> Continue shopping</Link>
          </div>
          <aside className="h-fit bg-[#eeefe9] p-6 sm:p-8">
            <h2 className="text-lg font-medium text-[#354237]">Order summary</h2>
            <div className="mt-6 flex justify-between text-sm text-[#73796f]"><span>Subtotal</span><span>${subtotal.toFixed(2)}</span></div>
            <div className="mt-3 flex justify-between text-sm text-[#73796f]"><span>Shipping</span><span>{subtotal >= 100 ? "Complimentary" : "Calculated at checkout"}</span></div>
            <div className="my-5 h-px bg-[#dfe2d9]" />
            <div className="flex justify-between text-sm font-medium text-[#354237]"><span>Total</span><span>${subtotal.toFixed(2)}</span></div>
            <p className="mt-3 text-xs leading-5 text-[#858a80]">Taxes and delivery are calculated at checkout.</p>
            <Link to="/checkout" className="mt-6 flex h-12 w-full items-center justify-center gap-2 bg-[#314638] text-xs font-medium uppercase tracking-[0.12em] text-white transition hover:bg-[#25372b]">Continue to checkout <ArrowRight size={14} /></Link>
          </aside>
        </div>
      )}
    </main>
  );
}
